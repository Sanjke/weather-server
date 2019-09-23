package command

import grails.validation.Validateable

class ExportCommand implements Validateable {

    String from
    String to
    Integer range
    String time
    String timeRange

    static constraints = {
        time nullable: true
        timeRange nullable: true
    }

}
