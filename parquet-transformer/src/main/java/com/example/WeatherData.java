package com.example;

public class WeatherData {
    int humidity;
    int temperature;
    int wind_speed;
    public int getHumidity() {
        return humidity;
    }
    public void setHumidity(int humidity) {
        this.humidity = humidity;
    }
    public int getTemperature() {
        return temperature;
    }
    public void setTemperature(int temperature) {
        this.temperature = temperature;
    }
    public int getWind_speed() {
        return wind_speed;
    }
    public void setWind_speed(int wind_speed) {
        this.wind_speed = wind_speed;
    }
    @Override
    public String toString() {
        return "WeatherData [humidity=" + humidity + ", temperature=" + temperature + ", wind_speed=" + wind_speed
                + "]";
    }

    
}
