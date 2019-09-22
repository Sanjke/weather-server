package model

import helper.ByteHelper
import org.joda.time.DateTime

class ReportDate {

    public short year
    public short month
    public short day
    public short hour
    public short minute
    public short second
    public short mSecond

    ReportDate(byte[] data) {
        year = ByteHelper.bytesToShort(Arrays.copyOfRange(data, 0, 2))
        month = ByteHelper.bytesToShort(Arrays.copyOfRange(data, 2, 4))
        day = ByteHelper.bytesToShort(Arrays.copyOfRange(data, 4, 6))
        hour = ByteHelper.bytesToShort(Arrays.copyOfRange(data, 6, 8))
        minute = ByteHelper.bytesToShort(Arrays.copyOfRange(data, 8, 10))
        second = ByteHelper.bytesToShort(Arrays.copyOfRange(data, 10, 12))
        mSecond = ByteHelper.bytesToShort(Arrays.copyOfRange(data, 12, 14))
    }

    void print() {
        System.out.println(year + "/" + month + "/" + day + " - " + hour + ":" + minute + ":" + second + "." + mSecond)
    }

    Date getDate() {
        return new DateTime()
                .withYear(year)
                .withMonthOfYear(month)
                .withDayOfMonth(day)
                .withHourOfDay(hour)
                .withMinuteOfHour(minute)
                .withSecondOfMinute(second)
                .withMillisOfSecond(mSecond)
                .toDate()
    }

}
