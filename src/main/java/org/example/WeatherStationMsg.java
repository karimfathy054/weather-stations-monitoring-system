package org.example;

public class WeatherStationMsg {
    private long station_id;
    private long s_no;//auto increment
    private BatteryStatus battery_status;
    private long status_timestamp;
    private WeatherData weather;

    public WeatherStationMsg(long stationId,long s_no,BatteryStatus batteryStatus, WeatherData weatherData) {
        this.station_id = stationId;
        this.s_no = s_no;
        this.battery_status = batteryStatus;
        this.status_timestamp = System.currentTimeMillis();
        this.weather = weatherData;
    }

    public long getStation_id() {
        return station_id;
    }

    public void setStation_id(long station_id) {
        this.station_id = station_id;
    }

    public long getS_no() {
        return s_no;
    }

    public void setS_no(long s_no) {
        this.s_no = s_no;
    }

    public BatteryStatus getBattery_status() {
        return battery_status;
    }

    public void setBattery_status(BatteryStatus battery_status) {
        this.battery_status = battery_status;
    }

    public long getStatus_timestamp() {
        return status_timestamp;
    }

    public void setStatus_timestamp(long status_timestamp) {
        this.status_timestamp = status_timestamp;
    }

    public WeatherData getWeather() {
        return weather;
    }

    public void setWeather(WeatherData weather) {
        this.weather = weather;
    }
}
