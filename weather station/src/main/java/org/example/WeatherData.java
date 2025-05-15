package org.example;

public class WeatherData {
    private final int humidity;
    private final int temperature;
    private final int wind_speed;

    public WeatherData(int temperature, int humidity, int windSpeed) {
        this.temperature = temperature;
        this.humidity = humidity;
        this.wind_speed = windSpeed;
    }

    public int getHumidity() {
        return humidity;
    }

    public int getTemperature() {
        return temperature;
    }

    public int getWind_speed() {
        return wind_speed;
    }
}
