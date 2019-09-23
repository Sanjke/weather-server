package weather.server

class BootStrap {

    WeatherService weatherService

    def init = { servletContext ->
        weatherService.generateReport()
    }
    def destroy = {
    }
}
