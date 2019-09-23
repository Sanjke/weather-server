package weather.server

import command.ExportCommand
import grails.util.Holders

class WeatherController {

    WeatherService weatherService

    def index(ExportCommand command) {
        if (command.validate()) {
            weatherService.exportReport(command)
        }
        render(view: "index", model: [files: FileExport.list([sort: "created", order: "desc"])])
    }

    def file(Long id) {
        String storage = Holders.config.getRequiredProperty('export', String)
        FileExport file = FileExport.get(id)
        render(file: new File(storage + file.file), fileName: file.file, contentType: "application/CSV")
    }

    def analyze() {
        weatherService.analyze()
        render "Analyze data started"
    }

    def download() {
        weatherService.downloadFiles()
        render "Download data started"
    }
}
