package com.example;

public class WeatherStationMsg {
    long station_id;
    long s_no;//auto increment
    String battery_status;
    long status_timestamp;
    WeatherData weather;
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
    public String getBattery_status() {
        return battery_status;
    }
    public void setBattery_status(String battery_status) {
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
    @Override
    public String toString() {
        return "WeatherStationMsg [station_id=" + station_id + ", s_no=" + s_no + ", battery_status=" + battery_status
                + ", status_timestamp=" + status_timestamp + ", weather=" + weather + "]";
    }

    
}
