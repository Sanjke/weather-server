package weather.server

import model.WeatherData

class WeatherReport {

    Date date

    Float windS
    Float windE
    Float windV

    Float temp
    Float press
    Float hum

    Integer height
    Short type

    Short err

    String file

    static constraints = {
    }

    static mapping = {
        sort date: "asc"
    }

    void setData(WeatherData data) {
        windE = data.windE
        windS = data.windS
        windV = data.windV
        temp = data.temp
        press = data.press
        hum = data.hum
        err = data.err
    }
}
