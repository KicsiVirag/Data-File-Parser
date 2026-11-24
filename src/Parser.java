import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Konzolos program, amely beolvas egy lazán strukturált, esetleg hibás
 * adatfájlt, és az adatokat oszloponként külön fájlba szortírozza.
 *
 * <h2>Használat</h2>
 * <pre>java Parser example.txt [-F elválasztó]</pre>
 * Az alapértelmezett elválasztó a {@code ;}. A {@code -F} másikat állít be
 * ({@code -F ,}, {@code -F "|"}, {@code -F '\t'}; a {@code -F,} alak is jó).
 *
 * <h2>Bemeneti formátum</h2>
 * <ol>
 * <li>1. sor: az oszlopok nevei.</li>
 * <li>2. sor: oszloponként egy típuskód, lásd {@link FieldType}.</li>
 * <li>3. sortól: az adatok.</li>
 * </ol>
 * Mivel a típussort is az elválasztó bontja, a reguláris kifejezés nem
 * tartalmazhatja az elválasztó karaktert; ilyenkor válasszunk másikat
 * {@code -F}-fel.
 *
 * <h2>Kimenet</h2>
 * A bemeneti fájl mellett létrejön a {@code <fájlnév kiterjesztés nélkül>_out}
 * mappa, benne oszloponként egy {@code <oszlopnév>.txt}, soronként egy
 * értékkel, a sorok eredeti sorrendjében. (Így a bemeneti fájl sosem
 * íródhat felül.)
 *
 * <h2>Végzetes hibák</h2>
 * Hiányzó fájlnév, olvashatatlan fájl, vagy az első két sorban üres név,
 * üres vagy ismeretlen típuskód (illetve ismétlődő név, rossz számú
 * típuskód). A hiba helye a szabványos hibakimenetre kerül, a kilépési kód
 * nem nulla, és nem jön létre kimeneti fájl.
 *
 * <h2>Az adatsorok javítási szabályai</h2>
 * Az oszlopokat balról jobbra töltjük, egy mutatóval a soron következő
 * értékre:
 * <ul>
 * <li>Üres érték: a típus alapértéke kerül be (lásd
 * {@link FieldType#emptyValue()}).</li>
 * <li>Érvényes érték: elfogadjuk.</li>
 * <li>Az aktuális oszlopra érvénytelen, de valamelyik <em>későbbi</em>
 * oszlopnak megfelelő érték azt jelenti, hogy az aktuális oszlop kimaradt:
 * az aktuális oszlop alapértéket kap, az érték pedig marad a későbbinek.
 * Ez a felcserélt szomszédokat is helyreállítja.</li>
 * <li>Az az érték, amely egyik későbbi oszlopnak sem felel meg, szemét, és
 * eldobjuk.</li>
 * <li>Az utolsó oszlop után maradt értékeket figyelmen kívül hagyjuk, a sor
 * végéről hiányzókat pedig alapértékkel pótoljuk.</li>
 * <li>A teljesen üres sorokat kihagyjuk (egyoszlopos fájlnál viszont az üres
 * sor üres érték).</li>
 * </ul>
 */
public class Parser {

    /** Az elválasztó, ha nincs {@code -F} megadva. */
    public static String DEFAULT_SEPARATOR = ";";

    /**
     * A program belépési pontja.
     * <p>
     * Menete: (1) ha nincs argumentum, hibát ír ki és kilép; (2) az első
     * argumentum a fájlnév; (3) a további argumentumok közül a {@code -F}
     * (vagy a hozzátapasztott {@code -Fxxx} alak) cseréli le az elválasztót
     * — a {@code \t} szövegben lévő karakterpárt valódi tabulátorra
     * alakítjuk; (4) ha a megadott fájl nem létezik vagy nem fájl, hiba;
     * (5) egyébként meghívja a {@link #process(File, String)} metódust, ami
     * elvégzi a tényleges feldolgozást. A hibaágak mind a szabványos
     * hibakimenetre ({@code System.err}) írnak, és {@code System.exit}-tel
     * nem nulla kilépési kóddal állnak le: 1 a használati/formátumhibáknál,
     * 2 az I/O hibáknál (pl. ha egy kimeneti fájl írása menet közben
     * meghiúsul).
     *
     * @param args a fájl neve, opcionálisan {@code -F elválasztó} követi
     */
    public static void main(String[] args) {

        if (args.length == 0) {
            System.err.println("Hiba: nincs megadva bemeneti fájl.");
            System.err.println("Használat: java Parser <fájl> [-F <elválasztó>]");
            System.exit(1);
            return;
        }
        String fileName = args[0];
        String separator = DEFAULT_SEPARATOR;

        // ---- a -F flag feldolgozása ----
        int i = 1;
        while (i < args.length) {
            String a = args[i];
            if (a.equals("-F")) {
                if (i + 1 >= args.length) {
                    System.err.println("Hiba: a -F után meg kell adni az elválasztót.");
                    System.exit(1);
                    return;
                }
                separator = args[i + 1].replace("\\t", "\t");
                i += 2;
            } else if (a.startsWith("-F")) {
                separator = a.substring(2).replace("\\t", "\t");
                i++;
            } else {
                System.err.println("Hiba: ismeretlen argumentum: '" + a + "'");
                System.exit(1);
                return;
            }
            if (separator.isEmpty()) {
                System.err.println("Hiba: az elválasztó nem lehet üres.");
                System.exit(1);
                return;
            }
        }

        File input = new File(fileName);
        if (!input.exists() || !input.isFile()) {
            System.err.println("Hiba: a(z) '" + fileName + "' nem létezik, vagy nem fájl.");
            System.exit(1);
            return;
        }

        try {
            process(input, separator);
        }
        catch (IOException e) {
            System.err.println("I/O hiba: " + e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            // Megjegyzés: a process() által dobott Exception üzenete már
            // tartalmazza a hiba helyét (pl. "1. sor: ..." vagy
            // "2. sor: ..."), ezért e.getMessage() itt kétszer jelenik meg
            // a kiírt szövegben.
            System.err.println("Hiba a(z) " + fileName + " fájl " + e.getMessage()
                    + ". sorában: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Ellenőrzi az első két sort, majd az összes adatsort szétosztja a
     * kimeneti fájlok között.
     * <p>
     * Lépései:
     * <ol>
     * <li>Beolvassa az 1. sort, és az elválasztó mentén feldarabolva
     * megkapja a mezőneveket. Minden névből előállít egy kimeneti
     * fájlnevet (a nem betű/szám karaktereket {@code _}-re cserélve, majd
     * {@code .txt}-t fűzve hozzá); ha egy név üres, vagy két név azonos
     * fájlnevet adna, hibát dob.</li>
     * <li>Beolvassa a 2. sort, és ugyanígy feldarabolva megkapja a
     * típuskódokat; darabszámuknak egyeznie kell a mezőnevek számával.
     * Minden kódot a {@link FieldType#parse(String)} alakít típussá; ha ez
     * hibát dob (üres vagy érvénytelen kód), a hiba tovább öröklődik.</li>
     * <li>Létrehozza a {@code <fájlnév>_out} mappát, és benne mezőnként
     * egy-egy {@link PrintWriter}-t nyit a megfelelő kimeneti fájlhoz.</li>
     * <li>Soronként beolvassa a további adatsorokat: a teljesen üres
     * sorokat kihagyja (kivéve, ha csak egy oszlop van), a nem üreseket az
     * elválasztó mentén feldarabolja, majd a {@link #sortRow} metódussal
     * hozzárendeli az értékeket az oszlopokhoz, és soronként kiírja azokat
     * a megfelelő fájlba.</li>
     * <li>A végén bezárja az összes írót és az olvasót, majd kiírja a
     * konzolra, hány sor és hány oszlop készült el.</li>
     * </ol>
     *
     * @param input     a bemeneti fájl
     * @param separator az értékeket elválasztó karakter(lánc)
     * @throws Exception ha az első két sor hibás (a hiba helyét és okát az
     *                    üzenet tartalmazza)
     * @throws IOException ha az olvasás vagy az írás sikertelen
     */
    static void process(File input, String separator) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(input)));

        try {
            // ---- 1. sor: nevek ----
            String line1 = reader.readLine();
            if (line1 == null) {
                // Megjegyzés: line1 itt null, ezért a kiírt üzenet
                // "null a fájl üres" lesz.
                throw new Exception(line1 + " a fájl üres");
            }
            if (!line1.isEmpty() && line1.charAt(0) == '\uFEFF') {
                line1 = line1.substring(1); // UTF-8 bájtsorrend-jel (BOM)
            }
            String[] names = line1.split(Pattern.quote(separator), -1);;
            String[] fileNames = new String[names.length];
            for (int i = 0; i < names.length; i++) {
                String name = names[i].trim();
                if (name.isEmpty()) {
                    throw new Exception("1. sor: " + (i + 1) + ". mezőnév üres");
                }
                String currentFileName = name.replaceAll("[^\\p{L}\\p{N}_.-]", "_") + ".txt";
                for (int j = 0; j < i; j++) {
                    // Mindkét nevet kisbetűssé alakítjuk, és úgy hasonlítjuk össze
                    if (fileNames[j].toLowerCase().equals(currentFileName.toLowerCase())) {
                        throw new Exception("1. sor: " + (i + 1) + ". mezőnév ('" + name
                                + "') megegyezik egy korábbival (ugyanabba a kimeneti fájlba kerülnének)");
                    }
                }
                fileNames[i] = currentFileName;
            }

            // ---- 2. sor: típusok ----
            String line2 = reader.readLine();
            if (line2 == null) {
                throw new Exception("2. sor: hiányzik a típusokat tartalmazó sor");
            }
            String[] codes = line2.split(Pattern.quote(separator), -1);;
            if (codes.length != names.length) {
                throw new Exception("2. sor: " + names.length
                        + " típuskód kellene (mezőnevenként egy), de " + codes.length + " van");
            }
            FieldType[] types = new FieldType[codes.length];
            for (int i = 0; i < codes.length; i++) {
                String code = codes[i].trim();
                if (code.isEmpty()) {
                    throw new Exception("2. sor: " + (i + 1) + ". típuskód ('"
                            + names[i].trim() + "' mező) üres");
                }
                try {
                    types[i] = FieldType.parse(code);
                } catch (Exception e) {
                    throw new Exception("2. sor: " + (i + 1) + ". típuskód ('"
                            + names[i].trim() + "' mező): " + e.getMessage());
                }
            }

            // ---- mezőnként egy kimeneti mappa és fájlok megnyitása ----

            // 1. Egyszerű mappakészítés: levágjuk a .txt-t és hozzátesszük, hogy _out
            String folderName = input.getName().replace(".txt", "") + "_out";
            File outDir = new File(folderName);
            outDir.mkdir(); // Létrehozza a mappát a program mappáján belül

            // 2. Kimeneti fájlok megnyitása a tanult egyszerű PrintWriter módszerrel
            PrintWriter[] writers = new PrintWriter[fileNames.length];
            for (int i = 0; i < fileNames.length; i++) {
                // A mappa nevét és a fájl nevét egy perjelel fűzzük össze
                writers[i] = new PrintWriter(folderName + "/" + fileNames[i]);
            }

            // ---- adatsorok beolvasása és kiírása ----
            int rows = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                // Teljesen üres sorok kihagyása
                if (line.trim().isEmpty() && names.length > 1) {
                    continue;
                }

                // Sor feldarabolása és javítása
                String[] values = sortRow(line.split(Pattern.quote(separator), -1), types);

                // Oszloponkénti kiírás a megfelelő fájlba
                for (int i = 0; i < values.length; i++) {
                    writers[i].println(values[i]);
                }
                rows++;
            }

            // ---- 3. Fájlok lezárása (Pontosan úgy, ahogy a Merge.java-ban) ----
            for (int i = 0; i < writers.length; i++) {
                writers[i].close();
            }
            reader.close();

            System.out.println("Kész: " + rows + " sor, " + names.length
                    + " oszlop kiírva ide: " + outDir.getName());
        } finally {
            reader.close();
        }
    }

    /**
     * Egy adatsor értékeit az oszlopokhoz rendeli, közben kijavítja a hibás
     * sorokat (a szabályokat lásd az osztály leírásában).
     * <p>
     * Egy {@code pos} mutatóval haladunk végig a sor nyers értékein
     * ({@code tokens}), oszloponként ({@code i}): ha a soron következő
     * érték üres, az adott oszlop is üres marad (alapértéket kap); ha
     * érvényes az adott oszlop típusára ({@link FieldType#accept}), azt
     * fogadjuk el, és a mutató továbblép; ha érvénytelen, de valamelyik
     * későbbi oszlopnak megfelelne ({@link #fitsLaterColumn}), akkor ezt az
     * oszlopot kihagyottnak tekintjük (alapértéket kap, a mutató nem lép),
     * így a következő oszlopnál ugyanez az érték próbálkozhat; egyébként az
     * érték szemétnek minősül, a mutató továbblép, és a ciklus a
     * következő tokennel próbálkozik ugyanahhoz az oszlophoz.
     *
     * @param tokens a sor nyers értékei
     * @param types  az oszlopok típusai
     * @return oszloponként pontosan egy kimeneti érték
     */
    static String[] sortRow(String[] tokens, FieldType[] types) {
        String[] result = new String[types.length];
        int pos = 0;
        for (int i = 0; i < types.length; i++) {
            String value = null;
            while (pos < tokens.length) {
                String t = tokens[pos].trim();
                if (t.isEmpty()) {      // kifejezetten üres érték
                    pos++;
                    break;
                }
                String accepted = types[i].accept(t);
                if (accepted != null) {     // érvényes érték
                    value = accepted;
                    pos++;
                    break;
                }
                if (fitsLaterColumn(t, types, i + 1)) {
                    break;                  // ez az oszlop kimaradt
                }
                pos++;                      // szemét: eldobjuk, próbáljuk a következőt
            }
            result[i] = value != null ? value : types[i].emptyValue();
        }
        return result;
    }

    /**
     * Megvizsgálja, hogy az adott érték illik-e valamelyik, a {@code from}
     * indextől kezdődő későbbi oszlop típusára. A {@link #sortRow} ezzel
     * dönti el, hogy egy aktuális oszlopra nem illő érték csak kimaradt
     * mezőt jelez (ilyenkor {@code true}), vagy egyszerűen szemét, amit el
     * lehet dobni (ilyenkor {@code false}).
     *
     * @param token a vizsgált érték
     * @param types az összes oszlop típusa
     * @param from  az index, amelytől kezdve keresünk (a jelenlegi oszlop
     *              utáni első index)
     * @return {@code true}, ha valamelyik későbbi oszlopnak megfelel az érték
     */
    private static boolean fitsLaterColumn(String token, FieldType[] types, int from) {
        for (int j = from; j < types.length; j++) {
            if (types[j].accept(token) != null) {
                return true;
            }
        }
        return false;
    }
}