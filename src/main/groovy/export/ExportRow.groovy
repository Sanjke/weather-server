package export

import weather.server.WeatherReport

import java.text.SimpleDateFormat

class ExportRow {
    Date d
    String dateN
    String timeN
    String date
    String time
    Float temp
    Float windS
    Integer dirS
    Float windE
    Integer dirE
    Float windV
    Integer dirV
    Float press
    Float hum
    Float err
    Long items = 0

    private static SimpleDateFormat format1 = new SimpleDateFormat("yyyyMMdd")
    private static SimpleDateFormat format2 = new SimpleDateFormat("HHmmss")
    private static SimpleDateFormat format3 = new SimpleDateFormat("dd.MM.yyyy")
    private static SimpleDateFormat format4 = new SimpleDateFormat("HH:mm:ss")


    ExportRow(WeatherReport report) {
        d = report.date
        dateN = format1.format(report.date)
        timeN = format2.format(report.date)
        date = format3.format(report.date)
        time = format4.format(report.date)
        temp = report.temp
        windS = report.windS
        dirS = windS > 0 ? 1 : 0
        windE = report.windE
        dirE = windE > 0 ? 1 : 0
        windV = report.windV
        dirV = windV > 0 ? 1 : 0
        press = report.press
        hum = report.hum
        err = report.err
    }

    ExportRow normalize() {
        temp = temp/items
        windS = windS/items
        windE = windE/items
        windV = windV/items
        press = press/items
        hum = hum/items
        return this
    }

    void sum(WeatherReport report) {
        temp += report.temp
        windS += report.windS
        windE += report.windE
        windV += report.windV
        press += report.press
        hum += report.hum
        items++
    }

    String export(Long id) {
        return "${id};${dateN};${timeN};${date};${time};${temp};${windS};${dirS};${windE};${dirE};${windV};${dirV};${press};${hum};${err}\n"
    }
}
