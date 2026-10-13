package Projekt.src;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BayesSpamFilter {

    Map<String, Integer> hamWoerter = new HashMap<>();
    Map<String, Integer> spamWoerter = new HashMap<>();

    int anzahlHam = 0;
    int anzahlSpam = 0;

    double alpha = 1e-6;
    double schwelleLogOdds = 0;


    public static void main(String[] args) throws IOException {
        BayesSpamFilter filter = new BayesSpamFilter();

            // Trainingsphase
            filter.anlernen(
                "data/ham-anlern.zip",
                "data/spam-anlern.zip"
            );

            // Kalibrierungsdaten einlesen
            List<String> hamKalibrierung =
                MailsEinlesen("data/ham-kallibrierung.zip");

            List<String> spamKalibrierung =
                MailsEinlesen("data/spam-kallibrierung.zip");

            // Optimale Werte bestimmen
            filter.kalibrieren(hamKalibrierung, spamKalibrierung);

            // Testdaten einlesen
            List<String> hamTest =
                MailsEinlesen("data/ham-test.zip");

            List<String> spamTest =
                MailsEinlesen("data/spam-test.zip");

            // Testdaten auswerten
            double[] ergebnis = filter.auswerten(hamTest, spamTest);

            // Ergebnisse ausgeben
            System.out.println("BAYES SPAM FILTER");

            System.out.println("Ham Trainingsmails: " + filter.anzahlHam);
            System.out.println("Spam Trainingsmails: " + filter.anzahlSpam);

            System.out.println("Alpha: " + filter.alpha);
            System.out.println("Schwelle Log-Odds: " + filter.schwelleLogOdds);

            double schwellenwert =
                1.0 / (1.0 + Math.exp(-filter.schwelleLogOdds));

        System.out.printf("Schwellenwert: %.2e%%%n", schwellenwert * 100);

            System.out.printf("Ham korrekt erkannt: %.2f%%%n", ergebnis[0] * 100);
            System.out.printf("Spam korrekt erkannt: %.2f%%%n", ergebnis[1] * 100);
        }

    // Liest alle Mails einer Zip-Datei eun und zählt für jedes Wort, in wie vielen Mails es vorkommt.

    public static int MailsLesen(String dateipfad, Map<String, Integer> wortZaehler) throws IOException {

        int anzahlMails = 0;

        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(dateipfad))) {

            ZipEntry eintrag;

            while ((eintrag = zip.getNextEntry()) != null) {
                if (eintrag.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                anzahlMails++;

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                zip.transferTo(buffer);

                String inhalt = buffer.toString(StandardCharsets.UTF_8);

                Set<String> woerter = new HashSet<>(Arrays.asList(inhalt.split("\\s+"))); // Jedes Wort wird pro Mail einmal gezählt

                for (String wort : woerter) {
                    if (!wort.isEmpty()) {
                        wortZaehler.merge(wort, 1, Integer::sum);

                    }
                }
                zip.closeEntry();
            }
        }
        return anzahlMails;
    }
    // Liest alle Mails einer Zip-Datei als Strings ein (ohne zu zählen). Wird für die Kalibrierungs- und Testdaten verwendet.
    public static List<String> MailsEinlesen(String dateipfad) throws IOException{
        List<String> mails = new ArrayList<>();

        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(dateipfad))){
            ZipEntry eintrag;
            while((eintrag = zip.getNextEntry()) != null){
                if (!eintrag.isDirectory()){
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    zip.transferTo(buffer);
                    mails.add(buffer.toString(StandardCharsets.UTF_8));
                }
                zip.closeEntry();
            }
        }
        return mails;
    }
    // Zerlegt eine Mail gleich wie in MailsLesen in ihre verschiedenen Wörter, damit Anlernen und Klassieren dieselber Worttrennung verwenden
    static Set<String> woerter(String mail) {
        Set<String> woerter = new HashSet<>(Arrays.asList(mail.split("\\s+")));
        woerter.remove("");
        return woerter;
    }

    // Baut die Wortzählungen auf und merkt sich die Anzahl Ham- und Spam-Mails
    public void anlernen(String hamZip, String spamZip) throws IOException{
        anzahlHam = MailsLesen(hamZip, hamWoerter);
        anzahlSpam = MailsLesen(spamZip, spamWoerter);
    }

    // Alpha verhindert die Wahrscheinlichkeit von 0 -> Falls ein Wort nur in Ham oder Spam vorkommt
    double anzahlInHam(String wort){
        return hamWoerter.containsKey(wort) ? hamWoerter.get(wort) : alpha;
    }
    double anzahlInSpam(String wort){
        return spamWoerter.containsKey(wort) ? spamWoerter.get(wort) : alpha;
    }

    public double logOdds(String mail){
        double summe = 0.0; // Gemäss der Quelle sind Ham und Spam zu beginn gleich wahrscheinlich (50%)

        for (String wort : woerter(mail)){
            if (!hamWoerter.containsKey(wort) && ! spamWoerter.containsKey(wort)) continue; // Wörter die in keiner Mail vorkommen, werden nicht berücksichtigt
            double pWortSpam = anzahlInSpam(wort) / anzahlSpam;
            double pWortHam = anzahlInHam(wort) / anzahlHam;
            summe += Math.log(pWortSpam) - Math.log(pWortHam); // Durch Log werden die Produkte als Summe berechnet
        }
        return summe;
    }
    // Berechnet die Spamwahrscheinlichkeit anhand der Log-Odds
    public double spamWahrscheinlichkeit(String mail) {
        return 1.0 / (1.0 + Math.exp(-logOdds(mail)));
    }
    // Eine Mail gilt als Spam, wenn ihre Spamwahrscheinlichkeit mindestens dem kalibrierten Schwellenwert entspricht
    public boolean istSpam(String mail) {
        double schwellenwert = 1.0 / (1.0 + Math.exp(-schwelleLogOdds));
        return spamWahrscheinlichkeit(mail) >= schwellenwert;
    }
    // Klassiert alle Mails und berechnet die Trefferquoten
    public double[] auswerten(List<String> hamMails, List<String> spamMails){
        int richtigHam = 0, richtigSpam = 0;
        for (String m : hamMails) if (!istSpam(m)) richtigHam++;
        for (String m : spamMails) if (istSpam(m)) richtigSpam++;
        return new double[] {(double) richtigHam / hamMails.size(), (double) richtigSpam / spamMails.size()};
    }

    public void kalibrieren (List<String> hamMails, List<String> spamMails) {
        double[] alphas = {1e-1, 1e-2, 1e-3, 1e-4, 1e-6, 1e-8, 1e-10, 1e-15, 1e-20, 1e-30, 1e-50}; // Verschiedene Alpha Werte und Schwellenwerte werden getestet
        double besteAlpha = alphas[0], besteSchwelle = 0, besterWert = -1;

        System.out.println("\nKalibrierung (bester Schwellenwert je alpha):");
        for (double a : alphas) {
            alpha = a;

            double[] lHam = new double[hamMails.size()];
            double[] lSpam = new double[spamMails.size()];
            for (int i = 0; i < lHam.length; i++) lHam[i] = logOdds(hamMails.get(i));
            for (int i = 0; i < lSpam.length; i++) lSpam[i] = logOdds(spamMails.get(i));

            double schwelle = 0, wert = -1, quoteHam = 0, quoteSpam = 0;
            for (int L = -1000; L <= 1000; L++) {
                int richtigHam = 0, richtigSpam = 0;
                for (double v : lHam) if (v < L) richtigHam++;
                for (double v : lSpam) if (v >= L) richtigSpam++;
                double qH = (double) richtigHam / lHam.length;
                double qS = (double) richtigSpam / lSpam.length;
                double w = (qH + qS) / 2; //Beide Mailklassen werden gleich gewichtet, unabhängig wie viele Ham und Spam Mails vorhanden sind
                if (w > wert + 1e-12 || (Math.abs(w - wert) <= 1e-12 && Math.abs(L) < Math.abs(schwelle))) {
                    wert = w;
                    schwelle = L;
                    quoteHam = qH;
                    quoteSpam = qS;
                }
            }
            System.out.printf(
                "alpha = %-6.0e Schwelle P >= %-12s Ham %6.2f%% Spam %6.2f%% Mittel %6.2f%%%n",
                a,
                alsWahrscheinlichkeit(schwelle),
                quoteHam * 100,
                quoteSpam * 100,
                wert * 100
            );
            if (wert > besterWert) {
                besterWert = wert;
                besteAlpha = a;
                besteSchwelle = schwelle;
            }
        }
            alpha = besteAlpha;
            schwelleLogOdds = besteSchwelle;
        }

    // Stellt Log-Odds lesbar als Wahrscheinlichkeit dar
    static String alsWahrscheinlichkeit(double L){
        if(L > 9) return String.format("1- %.1e", Math.exp(-L));
        if (L < -9) return String.format("%.1e",Math.exp(L));
        return String.format("%.4f",1 / (1 + Math.exp(-L)));
    }
}