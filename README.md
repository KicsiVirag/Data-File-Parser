# Java Data File Parser

A Java command-line application developed as a programming homework assignment. It processes loosely structured, delimiter-separated text files, validates individual fields, handles missing or malformed data, and exports each column to a separate text file.

## Features

- Configurable field separator (semicolon by default).
- Validation based on column-specific data types or custom regular expressions.
- Hungarian phone number normalization to the `+36` format.
- Type-specific default values for missing fields.
- Heuristic recovery of incomplete or malformed rows.
- Separate output files for each column, preserving row order.
- Header validation and command-line error reporting.
- No external dependencies.

## Supported data types

| Type code | Description | Default value |
| --- | --- | --- |
| `<string>` | Text | Empty string |
| `<number>` | Numeric value, including scientific notation | `0` |
| `<email>` | Email address | `nobody@example.com` |
| `<phone>` | Hungarian phone number | `+36000000000` |
| `<date>` | Date in `yyyy-MM-dd` format | `2000-01-01` |
| `<time>` | Time in `HH:mm:ss` format | `00:00:00` |
| Custom regex | Full regular expression match | Empty string |

## Project structure

```text
.
├── src/
│   ├── Parser.java
│   ├── FieldType.java
│   └── example.txt
├── tests/
├── example.txt
├── example_out/
├── test1_out/
└── test2_out/
```

- **Parser.java** — command-line argument processing, header validation, row recovery and file export.
- **FieldType.java** — data type parsing, value validation, normalization and default values.
- **tests/** — sample input files, including empty input and unknown-type cases.
- **example_out/** and **test*_out/** — example generated output directories.

## Getting started

The project is configured for OpenJDK 26 in IntelliJ IDEA. It uses only standard Java libraries and was also compiled and smoke-tested with OpenJDK 21.

From the project root, compile the source files:

```bash
javac -encoding UTF-8 -d build src/Parser.java src/FieldType.java
```

Run the parser:

```bash
java -cp build Parser example.txt
```

To use a different separator:

```bash
java -cp build Parser input.txt -F ","
java -cp build Parser input.txt -F "|"
java -cp build Parser input.txt -F "\t"
```

## Input format

The first line contains column names, the second line defines the corresponding data types, and subsequent lines contain data records.

```text
name;age;email;code
<string>;<number>;<email>;[A-Z]{3}-\d{2}
Alex;24;alex@example.com;ABC-12
Sam;;sam@example.com;XYZ-34
```

Running the parser on this example as `input.txt` generates `input_out/` with four files: `name.txt`, `age.txt`, `email.txt` and `code.txt`. For example, `age.txt` contains:

```text
24
0
```

## Data recovery and error handling

The parser processes fields from left to right. Valid values are accepted, explicitly empty fields receive defaults, and values that match a later column can cause the current column to be treated as missing. Unmatched tokens and surplus trailing values are discarded. Missing trailing fields receive defaults.

Invalid headers, unknown built-in type codes, invalid regular expressions and incorrect command-line arguments are reported as errors.

## Testing

```bash
java -cp build Parser tests/test1.txt
java -cp build Parser tests/test2.txt
java -cp build Parser tests/test3.txt
java -cp build Parser tests/test3_unknown_type.txt
```

The last two inputs are intended to exercise error handling. These are manual test cases, not an automated unit test suite.

## Current limitations

- Delimiter-separated text is split directly; quoted CSV fields and embedded delimiters are not supported.
- Row recovery is heuristic and cannot reliably reconstruct every possible malformed record.
- Date and time validation uses Java's default formatter resolution rather than strict resolution.
- The application is command-line based, with no graphical interface.
