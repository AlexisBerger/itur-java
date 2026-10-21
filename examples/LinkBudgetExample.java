import org.itur.Itur;
import org.itur.P618;
import org.itur.P676;
import org.itur.P837;
import org.itur.P838;

/**
 * Run (after "mvn package"):
 *   java -cp target/classes examples/LinkBudgetExample.java
 */
public class LinkBudgetExample {
    public static void main(String[] args) {
        double lat = 48.85;      // Paris
        double lon = 2.35;
        double f = 20.0;         // GHz
        double el = 35.0;        // degrees
        double p = 0.1;          // % of an average year
        double dishDiameter = 1.2; // m

        Itur.Attenuation a = Itur.atmosphericAttenuationSlantPath(lat, lon, f, el, p, dishDiameter);
        System.out.printf("Exceeded %.2f%% of the time at %.1f GHz, %.0f deg elevation:%n", p, f, el);
        System.out.printf("  gases        %6.2f dB%n", a.gas());
        System.out.printf("  clouds       %6.2f dB%n", a.cloud());
        System.out.printf("  rain         %6.2f dB%n", a.rain());
        System.out.printf("  scintillation%6.2f dB%n", a.scintillation());
        System.out.printf("  TOTAL        %6.2f dB%n", a.total());

        // The building blocks can be used on their own
        double r001 = P837.rainfallRate(lat, lon, 0.01);                       // mm/h
        double gammaR = P838.rainSpecificAttenuation(r001, f, el, 45.0);      // dB/km
        double ar001 = P618.rainAttenuation(lat, lon, f, el);                 // dB, p = 0.01 %
        double gas = P676.gaseousAttenuationSlantPath(f, el, 7.5, 1013.25, 288.15, null, null, P676.Mode.EXACT);
        System.out.printf("%nR0.01 = %.1f mm/h, gamma_R = %.2f dB/km, A_rain(0.01%%) = %.2f dB, A_gas(exact) = %.2f dB%n",
            r001, gammaR, ar001, gas);

        // Same total with some measured inputs and without scintillation
        Itur.Attenuation custom = Itur.atmosphericAttenuationSlantPath(lat, lon, f, el, p, dishDiameter,
            Itur.Options.builder().hsKm(0.12).r001MmPerHour(35).include(true, true, true, false).build());
        System.out.printf("Custom inputs: total %.2f dB%n", custom.total());
    }
}
