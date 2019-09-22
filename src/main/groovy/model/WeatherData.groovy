package model

import helper.ByteHelper

class WeatherData {

    public float temp
    public float windS
    public float windE
    public float windV
    public float press
    public float hum
    public byte err

    WeatherData(byte[] data) {
        short val = ByteHelper.bytesToShort(Arrays.copyOfRange(data, 0, 2))
        temp = val / 100F
        val = ByteHelper.bytesToShort(Arrays.copyOfRange(data, 2, 4))
        windS = val / 100F
        val = ByteHelper.bytesToShort(Arrays.copyOfRange(data, 4, 6))
        windE = val / 100F
        val = ByteHelper.bytesToShort(Arrays.copyOfRange(data, 6, 8))
        windV = val / 100F
        val = ByteHelper.bytesToShort(Arrays.copyOfRange(data, 8, 10))
        press = val / 10F
        val = ByteHelper.bytesToShort(Arrays.copyOfRange(data, 10, 12))
        hum = val / 100F
        err = data[12]
    }

    void print() {
        System.out.println(temp + ":" + windS + ":" + windE + ":" + windV + ":" + press + ":" + hum + ":" + err)
    }

}
