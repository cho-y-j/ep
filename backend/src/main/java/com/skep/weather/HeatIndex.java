package com.skep.weather;

/**
 * 기상청 여름철 체감온도 공식.
 * 습구온도 Tw(Stull 근사) → 체감온도. 기온 Ta(℃), 상대습도 RH(%).
 */
final class HeatIndex {
    private HeatIndex() {}

    static double feelsLike(double ta, double rh) {
        double tw = ta * Math.atan(0.151977 * Math.sqrt(rh + 8.313659))
                + Math.atan(ta + rh) - Math.atan(rh - 1.67633)
                + 0.00391838 * Math.pow(rh, 1.5) * Math.atan(0.023101 * rh)
                - 4.686035;
        return -0.2442 + 0.55399 * tw + 0.45535 * ta
                - 0.0022 * tw * tw + 0.00278 * tw * ta + 3.0;
    }
}
