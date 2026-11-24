import java.util.*;
import java.util.regex.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.*;

/**
 * Egy oszlop adattípusa, ahogy a bemeneti fájl második sorában meg van adva.
 * <p>
 * Egy {@code FieldType} objektum önmagában semmit nem olvas be; csak azt
 * tudja megmondani egy adott szövegről ({@link #accept(String)}), hogy
 * érvényes-e az adott típusra, illetve mi legyen az alapértelmezett értéke,
 * ha a mező üres marad ({@link #emptyValue()}). A {@link Parser} osztály
 * {@code sortRow} metódusa ezt a két metódust hívogatja mezőnként.
 * <p>
 * Támogatott típuskódok (kis- és nagybetűre nem érzékenyek):
 * <ul>
 * <li>{@code <string>} - tetszőleges karakterlánc</li>
 * <li>{@code <number>} - egész vagy lebegőpontos szám (tizedespont)</li>
 * <li>{@code <email>} - e-mail cím</li>
 * <li>{@code <phone>} - magyar telefonszám, {@code +36...} alakra normalizálva</li>
 * <li>{@code <date>} - létező naptári dátum, {@code ÉÉÉÉ-HH-NN}</li>
 * <li>{@code <time>} - létező időpont, {@code ÓÓ:PP:MM}</li>
 * </ul>
 * Minden más szöveget reguláris kifejezésként értelmezünk, amelyre a teljes
 * értéknek illeszkednie kell.
 */
public class FieldType {

    /**
     * Az adattípus fajtái. A {@code REGEX} azt jelenti, hogy a típuskód nem
     * a fenti hat beépített kód egyike volt, hanem egy szabad reguláris
     * kifejezés, amelyet a {@link #pattern} mezőben tárolunk.
     */
    private enum Kind {
        STRING, NUMBER, EMAIL, PHONE, DATE, TIME, REGEX
    }

    /** Ennek a mezőnek a fajtája. */
    private Kind kind;

    /** REGEX fajtánál a reguláris kifejezés szövege; egyébként {@code null}. */
    private String pattern; // REGEX típusnál a reguláris kifejezés szövege

    /**
     * Privát konstruktor. Kívülről csak a {@link #parse(String)} metóduson
     * keresztül hozható létre {@code FieldType} példány.
     *
     * @param kind    a típus fajtája
     * @param pattern REGEX fajtánál a minta szövege, egyébként {@code null}
     */
    private FieldType(Kind kind, String pattern) {
        this.kind = kind;
        this.pattern = pattern;
    }

    /**
     * A fájl második sorában álló típuskódot típussá alakítja.
     * <p>
     * Sorban végignézi a hat beépített kódot ({@code <string>},
     * {@code <number>}, {@code <email>}, {@code <phone>}, {@code <date>},
     * {@code <time>}); ha valamelyikre illik a (kisbetűssé alakított) kód,
     * azt a fajtát adja vissza. Ha a kód {@code <valami>} alakú, de nem a
     * hat ismert kód egyike, feltételezzük, hogy elírás történt, és hibát
     * dobunk — szándékosan nem próbáljuk reguláris kifejezésként értelmezni,
     * mert az könnyen elrejtene egy elgépelést. Minden egyéb esetben a
     * szöveget reguláris kifejezésként próbáljuk lefordítani; ha a fordítás
     * ({@link Pattern#compile(String)}) hibát dob, a típuskód érvénytelen.
     *
     * @param code a (nem üres) típuskód vagy reguláris kifejezés
     * @return a megfelelő típus
     * @throws Exception ha a kód {@code <...>} alakú, de nem a hat ismert
     *                    kód egyike, vagy ha érvénytelen reguláris kifejezés
     */
    public static FieldType parse(String code) throws Exception {
        String c = code.trim();
        String lower = c.toLowerCase();

        if (lower.equals("<string>")) {
            return new FieldType(Kind.STRING, null);
        }
        if (lower.equals("<number>")) {
            return new FieldType(Kind.NUMBER, null);
        }
        if (lower.equals("<email>")) {
            return new FieldType(Kind.EMAIL, null);
        }
        if (lower.equals("<phone>")) {
            return new FieldType(Kind.PHONE, null);
        }
        if (lower.equals("<date>")) {
            return new FieldType(Kind.DATE, null);
        }
        if (lower.equals("<time>")) {
            return new FieldType(Kind.TIME, null);
        }

        // Ha <valami> alakú, de nem a fenti kódok egyike, valószínűleg elírás,
        // nem reguláris kifejezésnek szánták.
        if (c.startsWith("<") && c.endsWith(">")) {
            throw new Exception("ismeretlen típuskód: '" + c + "'");
        }

        // Ellenőrizzük, hogy a szöveg érvényes reguláris kifejezés-e.
        try {
            Pattern.compile(c);
        } catch (PatternSyntaxException e) {
            throw new Exception("érvénytelen reguláris kifejezés: '" + c + "'");
        }
        return new FieldType(Kind.REGEX, c);
    }

    /**
     * Ellenőriz egy nem üres, levágott szélű értéket ehhez a típushoz.
     * <p>
     * Minden fajtánál ugyanaz a séma: lefordítjuk a mintát
     * ({@code Pattern.compile}), ráillesztjük a szövegre ({@code matcher}),
     * és {@code matches()} dönti el, hogy a teljes szöveg illeszkedik-e a
     * mintára. Ha igen, az érték érvényes, és (a telefonszám kivételével)
     * változatlanul adjuk vissza. A telefonszámnál a minta egy zárójeles
     * csoportot ({@code group(1)}) fog a számjegyekre, ebből építjük fel az
     * egységes {@code +36...} alakot. Ha a minta nem illeszkedik,
     * {@code null}-t adunk vissza; ezt a hívó ({@link Parser#sortRow}) úgy
     * értelmezi, hogy ez az érték nem ehhez az oszlophoz tartozik.
     *
     * @param token a nyers érték
     * @return az érték úgy, ahogy a kimeneti fájlba kerül (a telefonszám
     *         normalizálva), vagy {@code null}, ha az érték nem felel meg
     *         a típusnak
     */
    public String accept(String token) {
        if (kind == Kind.STRING) {
            return token;
        }

        if (kind == Kind.NUMBER) {
            Pattern p = Pattern.compile("[+-]?(\\d+(\\.\\d+)?|\\.\\d+)([eE][+-]?\\d+)?");
            Matcher m = p.matcher(token);
            if (m.matches()) {
                return token;
            }
            else {
                return null;
            }
        }

        if (kind == Kind.EMAIL) {
            Pattern p = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");
            Matcher m = p.matcher(token);
            if (m.matches()) {
                return token;
            }
            else {
                return null;
            }
        }

        if (kind == Kind.PHONE) {
            // előhívó (+36, 0036 vagy 06), majd 8-9 számjegy, az első nem 0
            Pattern p = Pattern.compile("(?:\\+36|0036|06)([1-9]\\d{7,8})");
            Matcher m = p.matcher(token);
            if (!m.matches()) {
                return null;
            }
            return "+36" + m.group(1);
        }

        if (kind == Kind.DATE) {
            if (isValidDate(token)) {
                return token;
            }
            else {
                return null;
            }
        }

        if (kind == Kind.TIME) {
            if (isValidTime(token)) {
                return token;
            }
            else {
                return null;
            }
        }

        // REGEX
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(token);
        if (m.matches()) {
            return token;
        }
        else {
            return null;
        }
    }

    /**
     * Ellenőrzi, hogy a szöveg ÉÉÉÉ-HH-NN alakú, valódi naptári dátum-e
     * (pl. a 2023-02-30 nem az, mert februárnak nincs 30 napja). A
     * {@link LocalDate#parse(CharSequence, DateTimeFormatter)} hívás akkor
     * dob {@link DateTimeParseException}-t, ha a szöveg nem illik a
     * {@code yyyy-MM-dd} formátumra, vagy ha illik rá, de nem létező
     * dátumot ír le (pl. 13. hónap, vagy február 30.).
     *
     * @param token a vizsgált szöveg
     * @return {@code true}, ha érvényes dátum
     */
    private static boolean isValidDate(String token) {
        try {
            // Megpróbálja a beépített motorral ÉÉÉÉ-HH-NN formátumban értelmezni
            LocalDate.parse(token, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            return true;
        } catch (DateTimeParseException e) {
            // Ha hibás a formátum, vagy nem létezik a nap (pl. február 30)
            return false;
        }
    }

    /**
     * Ellenőrzi, hogy a szöveg ÓÓ:PP:MM alakú, valódi időpont-e
     * (pl. a 25:61:00 nem az, mert nincs 25. óra).
     *
     * @param token a vizsgált szöveg
     * @return {@code true}, ha érvényes időpont
     */
    private static boolean isValidTime(String token) {
        try {
            // Megpróbálja értelmezni ÓÓ:PP:MM formátumban
            LocalTime.parse(token, DateTimeFormatter.ofPattern("HH:mm:ss"));
            return true;
        } catch (DateTimeParseException e) {
            // Ha nem számokból áll, rossz a formátum, vagy lehetetlen időpont (pl. 25:00:00)
            return false;
        }
    }

    /**
     * Visszaadja azt az értéket, amely hiányzó vagy érvénytelen adat esetén
     * a kimeneti fájlba kerül. A {@link Parser#sortRow} akkor hívja meg,
     * amikor az adott oszlophoz egyetlen bemeneti érték sem jutott.
     *
     * @return a típus alapértelmezett értéke
     */
    public String emptyValue() {
        if (kind == Kind.NUMBER) {
            return "0";
        }
        if (kind == Kind.EMAIL) {
            return "nobody@example.com";
        }
        if (kind == Kind.PHONE) {
            return "+36000000000";
        }
        if (kind == Kind.DATE) {
            return "2000-01-01";
        }
        if (kind == Kind.TIME) {
            return "00:00:00";
        }
        return ""; // STRING és REGEX
    }
}