package weather.server

import command.ExportCommand
import grails.gorm.transactions.Transactional
import grails.util.Holders
import helper.ByteHelper
import model.ExportType
import model.ReportDate
import model.WeatherData
import org.hibernate.SessionFactory
import org.hibernate.StatelessSession
import org.hibernate.Transaction
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

import javax.annotation.PreDestroy
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Transactional
class WeatherService {

    private String storage = Holders.config.getRequiredProperty('storage', String)
    private String exportStorage = Holders.config.getRequiredProperty('export', String)
    ExecutorService executor = Executors.newFixedThreadPool(4)
    SessionFactory sessionFactory
    ConcurrentLinkedQueue<String> sources = new ConcurrentLinkedQueue<>()
    ConcurrentLinkedQueue<String> urls = new ConcurrentLinkedQueue<>()
    private long processStart
    private int files = 0
    private int allFiles = 0


    @PreDestroy
    void shutdown() {
        executor.shutdownNow()
    }

    void exportReport(ExportCommand command) {
        FileExport export = new FileExport()
        SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss")
        if (command.time) {
            export.type = ExportType.TIME
            export.dateFrom = format.parse(command.from + "_" + command.time)
            export.dateTo = format.parse(command.to + "_" + command.time)
            format = new SimpleDateFormat("HH.mm.ss")
            export.range = format.parse(command.time).time / 1000
            format = new SimpleDateFormat("HH.mm.ss.SSS")
            export.timeRange = format.parse(command.timeRange)
            format = new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss")
            export.file = "AMK_MG--${format.format(new Date())}--${format.format(export.dateFrom)}--${format.format(export.dateTo)}--${command.range}--${command.timeRange}.dat"
        } else {
            export.type = ExportType.RANGE
            format = new SimpleDateFormat("yyyy.MM.dd HH.mm.ss")
            export.dateFrom = format.parse(command.from)
            export.dateTo = format.parse(command.to)
            export.range = command.range
            format = new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss")
            export.file = "AMK_MG--${format.format(new Date())}--${format.format(export.dateFrom)}--${format.format(export.dateTo)}--${export.range}.dat"
        }
        generateExport()
        File file = new File(exportStorage + export.file)
        file.createNewFile()
        export.save()
    }

    void generateExport() {
        Path directory = new File(exportStorage).toPath()
        if (Files.notExists(directory)) Files.createDirectories(directory)
    }

    void generateRangeExport() {

    }

    void analyze() {
        processStart = System.currentTimeMillis()
        try {
            File dir = new File(storage)
            allFiles = dir.listFiles().size()
            sources.addAll(dir.listFiles().collect { it.absolutePath })
            for (int i = 0; i < 4; i++)
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
                    FileInputStream fis = new FileInputStream(file)
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

                    System.out.println("Start")
                    while (notRead > 14) {
                        fis.read(w)
                        WeatherReport report = new WeatherReport(date: new Date((Long) (startMillis / 10)), height: height, type: type, file: file.name)
                        report.setData(new WeatherData(w))
                        session.insert(report)

                        notRead -= 13
                        startMillis += 125
                    }
                    System.out.println("End")

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
