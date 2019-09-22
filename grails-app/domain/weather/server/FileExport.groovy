package weather.server

import model.ExportType

class FileExport {

    String file
    Boolean done = false
    ExportType type
    Date created = new Date()
    Date dateFrom
    Date dateTo
    Integer range
    Long time
    Long timeRange

    static constraints = {
        time nullable: true
        timeRange nullable: true
        dateTo nullable: true
    }

    static mapping = {
        created date: "desc"
    }

}
