package weather.server

import command.ExportCommand
import export.ExportRow
import export.TimeExportData
import grails.gorm.transactions.Transactional
import grails.util.Holders
import helper.ByteHelper
import model.ExportType
import model.ReportDate
import model.WeatherData
import org.hibernate.SessionFactory
import org.hibernate.StatelessSession
import org.hibernate.Transaction
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

import javax.annotation.PreDestroy
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Transactional
class WeatherService {

    private String storage = Holders.config.getRequiredProperty('storage', String)
    private String exportStorage = Holders.config.getRequiredProperty('export', String)
    ThreadPoolExecutor executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    ScheduledExecutorService fileExecutor = Executors.newSingleThreadScheduledExecutor()
    SessionFactory sessionFactory
    ConcurrentLinkedQueue<String> sources = new ConcurrentLinkedQueue<>()
    ConcurrentLinkedQueue<String> urls = new ConcurrentLinkedQueue<>()
    ConcurrentLinkedQueue<TimeExportData> timeExports = new ConcurrentLinkedQueue<>()
    ConcurrentLinkedQueue<ExportRow> rows = new ConcurrentLinkedQueue<>()
    Long fileId
    private long processStart
    private int files = 0
    private int allFiles = 0


    @PreDestroy
    void shutdown() {
        executor.shutdownNow()
    }

    void exportReport(ExportCommand command) {
        if (fileId)
            return
        FileExport export = new FileExport()
        SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss")
        if (command.time) {
            export.type = ExportType.TIME
            export.dateFrom = format.parse(command.from + "_" + command.time)
            export.dateTo = format.parse(command.to + "_" + command.time)
            export.range = command.range
            Long multiplier = 60*60L
            command.time.split("\\.").each {
                export.time += multiplier * Long.parseLong(it)
                multiplier = multiplier / 60
            }
            multiplier = 60*60000L
            command.timeRange.split("\\.").each {
                export.timeRange += multiplier * Long.parseLong(it)
                multiplier = multiplier > 1000 ? (multiplier / 60) : 1
            }
            format = new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss")
            export.file = "AMK_MG--${format.format(new Date())}--${format.format(export.dateFrom)}--${format.format(export.dateTo)}--${command.time}--${command.timeRange}.dat"
        } else {
            export.type = ExportType.RANGE
            format = new SimpleDateFormat("yyyy.MM.dd HH.mm.ss")
            export.dateFrom = format.parse(command.from)
            export.dateTo = format.parse(command.to)
            export.range = command.range
            format = new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss")
            export.file = "AMK_MG--${format.format(new Date())}--${format.format(export.dateFrom)}--${format.format(export.dateTo)}--${export.range}.dat"
        }
        export.save(flush: true)
        generateExport(export)
    }

    void generateExport(FileExport export) {
        Path directory = new File(exportStorage).toPath()
        if (Files.notExists(directory)) Files.createDirectories(directory)

        if (export.type == ExportType.RANGE) {
            generateRangeExport(export)
        } else if (export.type == ExportType.TIME) {
            generateTimeReport(export)
        }
        for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++)
            executor.execute {
                processTimeExport()
            }
        fileId = export.id
    }

    void generateRangeExport(FileExport export) {
        DateTime date = new DateTime(export.dateFrom)
        DateTime endDate = new DateTime(export.dateTo)
        if (export.range > 0) {
            DateTime nextDate = date.plusMinutes(export.range * 10)
            while (nextDate.isBefore(endDate)) {
                TimeExportData exportData = new TimeExportData(dateFrom: date.toDate(), dateTo: nextDate.toDate(), range: export.range)
                timeExports.add(exportData)
                date = nextDate
                nextDate = nextDate.plusMinutes(export.range * 10)
            }
        }
        TimeExportData exportData = new TimeExportData(dateFrom: date.toDate(), dateTo: endDate.toDate(), range: export.range)
        timeExports.add(exportData)
    }

    void generateTimeReport(FileExport export) {
        DateTime date = new DateTime(export.dateFrom)
        DateTime endDate = new DateTime(export.dateTo)
        date.withMillisOfDay((export.time * 1000).toInteger())
        endDate.withMillisOfDay(((export.time + 1) * 1000).toInteger())
        while (date.isBefore(endDate)) {
            TimeExportData exportData = new TimeExportData(dateFrom: date.minusMillis(export.timeRange.toInteger()).toDate(),
                    dateTo: date.plusMillis(export.timeRange.toInteger()).toDate(), range: export.range)
            timeExports.add(exportData)
            date = date.plusDays(1)
        }
    }

    private void processTimeExport() {
        TimeExportData exportData
        if ((exportData = timeExports.poll()) == null) {
            return
        }
        WeatherReport.withTransaction {
            List<WeatherReport> weathers = WeatherReport.findAllByDateBetween(exportData.dateFrom, exportData.dateTo, [sort: "date", order: "asc"])
            if (weathers.size() > 0) {
                if (exportData.range == 0) {
                    weathers.each {
                        rows.add(new ExportRow(it))
                    }
                } else {
                    DateTime date = new DateTime(weathers.get(0).date)
                    ExportRow row
                    weathers.each {
                        if (date.millis <= it.date.time) {
                            date = date.plusMinutes(exportData.range.toInteger())
                            if (row)
                                rows.add(row.normalize())
                            row = new ExportRow(it)
                        } else {
                            row.sum(it)
                        }
                    }
                    rows.add(row.normalize())
                }
            }
        }
        processTimeExport()
    }

    void generateReport() {
        fileExecutor.schedule({
            generateReport()
        }, 5, TimeUnit.SECONDS)
        if (fileId && executor.getActiveCount() == 0) {
            FileExport.withTransaction {
                FileExport fileExport = FileExport.get(fileId)
                FileWriter fw = new FileWriter(exportStorage + fileExport.file)
                Long id = 1
                rows.sort { it.d }.each {
                    fw.append(it.export(id++))
                }
                fw.flush()
                fw.close()
                fileExport.done = true
                fileExport.save()
                fileId = null
                System.out.println("${fileExport.file} was generated")
            }
        }
    }

    void analyze() {
        System.out.println("Start analyze using ${Runtime.getRuntime().availableProcessors()} CPUs")
        processStart = System.currentTimeMillis()
        try {
            File dir = new File(storage)
            allFiles = dir.listFiles().size()
            sources.addAll(dir.listFiles().collect { it.absolutePath })
            for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++)
                executor.execute {
                    processFile()
                }

        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    private void processFile() {
        if (sources.isEmpty()) return
        File file = new File(sources.poll())
        WeatherReport.withTransaction {
            if (file.exists() && !WeatherReport.findByFile(file.name)) {
                System.out.println("Start process ${file.absolutePath}")
                long startTime = System.currentTimeMillis()

                try {
                    InputStream fis = new ByteArrayInputStream(Files.readAllBytes(file.toPath()))


                    long notRead = file.length()
                    byte[] d = new byte[14]
                    byte[] w = new byte[13]
                    byte[] h = new byte[2]


                    fis.read(d)
                    notRead -= 14
                    ReportDate start = new ReportDate(d)
                    Long startMillis = start.date.time * 10

                    fis.read(h)
                    Integer height = ByteHelper.bytesToShort(h)
                    notRead -= 2

                    Short type = (byte) fis.read()
                    notRead--

                    StatelessSession session = sessionFactory.openStatelessSession()
                    Transaction tx = session.beginTransaction()

                    while (notRead > 14) {
                        fis.read(w)
                        WeatherReport report = new WeatherReport(date: new Date((Long) (startMillis / 10)), height: height, type: type, file: file.name)
                        report.setData(new WeatherData(w))
                        session.insert(report)

                        notRead -= 13
                        startMillis += 125
                    }

                    tx.commit()
                    session.close()
                } catch (Exception ex) {
                    System.out.println("File error ${file.absolutePath}")
                    ex.printStackTrace()
                }


                System.out.println("File " + file.absolutePath + " was processed")
                System.out.println("Start time: ${startTime}")
                System.out.println("End time: ${System.currentTimeMillis()}")
                System.out.println("Duration: ${System.currentTimeMillis() - startTime}")

                System.out.println()

                System.out.println("Absolute statistic:")
                System.out.println("Files: ${files++} / ${allFiles}")
                System.out.println("Duration: ${System.currentTimeMillis() - processStart}")
            } else {
                System.out.println("File " + file.absolutePath + " not exists or already processed")
            }
        }
        processFile()
    }


    void downloadFiles() {
        Path directory = new File(storage).toPath()
        if (Files.notExists(directory)) Files.createDirectories(directory)

        executor.execute {
            try {
                downloadUrl("http://amk030.imces.ru/meteodata/AMK_030_BIN/")
            } catch (Exception e) {
                e.printStackTrace()
            }
        }
    }

    private void downloadUrl(String url) {
        if (!urls.contains(url)) {
            urls.add(url)
            System.out.println("Working on ${url}")
            Document doc = Jsoup.connect(url).get()
            Elements links = doc.getElementsByTag("a")
            for (Element link : links) {
                String href = link.attr("href")
                if (href.contains(".19B")) {
                    File file = new File(storage + href)
                    if (!file.exists()) {
                        executor.execute {
                            System.out.println("Downloading ${url}/${href}")
                            url.concat(href).toURL().withInputStream {
                                file << it
                            }
                            System.out.println("Downloaded ${url}${href}")
                        }
                    }
                } else if (href.contains("20") && !href.contains(".") && !link.text().contains("Parent")) {
                    executor.execute {
                        downloadUrl(url + href)
                    }
                }
            }
            System.out.println("Url ${url} was worked")
        } else {
            System.out.println("Url ${url} already processed")
        }
    }

}
