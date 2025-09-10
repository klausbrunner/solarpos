package net.e175.klaus.formatter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FormatterTest {

  @BeforeAll
  static void setupLocale() {
    // Ensure tests run with US locale for consistent decimal formatting
    Locale.setDefault(Locale.US);
  }

  // Sample data record class with various types
  record SampleData(String name, int count, double value, ZonedDateTime timestamp) {}

  // Create sample data with different types
  private SampleData createSampleData() {
    return new SampleData(
        "Test Item", 42, 123.456789, ZonedDateTime.parse("2023-05-01T12:34:56+02:00"));
  }

  // Create field descriptors for the sample data
  private List<FieldDescriptor<SampleData>> createFieldDescriptors() {
    return List.of(
        new FieldDescriptor<>("name", SampleData::name),
        new FieldDescriptor<>("count", SampleData::count),
        new FieldDescriptor<>("value", SampleData::value),
        // Default precision for double
        new FieldDescriptor<>("timestamp", SampleData::timestamp)
            .withHint("pattern", "yyyy-MM-dd HH:mm:ss"));
  }

  @Test
  void testJsonFormatter() throws IOException {
    var data = createSampleData();
    var fields = createFieldDescriptors();

    // Add a field with custom format hint
    var fieldsWithCustomFormat =
        List.of(
            fields.get(0), // name
            fields.get(1), // count
            fields.get(2).withHint("precision", 2), // value with 2 decimal places
            fields.get(3) // timestamp with default format
            );

    var sb = new StringBuilder();
    var formatter = new JsonFormatter<SampleData>(SerializerRegistry.forJson());

    // Test with all fields
    formatter.format(
        fieldsWithCustomFormat,
        List.of("name", "count", "value", "timestamp"),
        Stream.of(data),
        sb);

    // Verify JSON structure and values
    var json = JsonParser.parseString(sb.toString()).getAsJsonObject();
    assertEquals("Test Item", json.get("name").getAsString());
    assertEquals(42, json.get("count").getAsInt());
    assertEquals("123.46", json.get("value").getAsString());
    assertEquals("2023-05-01T12:34:56+02:00", json.get("timestamp").getAsString());

    // Test with subset of fields
    sb = new StringBuilder();
    formatter.format(
        fieldsWithCustomFormat,
        List.of("name", "value"), // Only include name and value
        Stream.of(data),
        sb);

    json = JsonParser.parseString(sb.toString()).getAsJsonObject();
    assertEquals(2, json.size());
    assertTrue(json.has("name"));
    assertTrue(json.has("value"));
    assertFalse(json.has("count"));
    assertFalse(json.has("timestamp"));
  }

  @Test
  void testCsvFormatter() throws IOException {
    var data = createSampleData();
    var fields = createFieldDescriptors();

    // Add a field with custom format hint
    var fieldsWithCustomFormat =
        List.of(
            fields.get(0), // name
            fields.get(1), // count
            fields.get(2).withHint("precision", 3), // value with 3 decimal places
            fields.get(3).withHint("pattern", "yyyy-MM-dd") // timestamp with date only
            );

    var sb = new StringBuilder();
    var formatter = new CsvFormatter<SampleData>(SerializerRegistry.forCsv(), true); // with headers

    // Test with all fields
    formatter.format(
        fieldsWithCustomFormat,
        List.of("name", "count", "value", "timestamp"),
        Stream.of(data),
        sb);

    // Parse CSV to verify structure
    var csvParser =
        CSVParser.parse(
            sb.toString(), CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get());

    var records = csvParser.getRecords();
    assertEquals(1, records.size());

    var record = records.getFirst();
    assertEquals("Test Item", record.get("name"));
    assertEquals("42", record.get("count"));
    assertEquals("123.457", record.get("value")); // 3 decimal places
    assertEquals("2023-05-01", record.get("timestamp")); // Date only format

    // Test with subset of fields and no headers
    sb = new StringBuilder();
    formatter = new CsvFormatter<>(SerializerRegistry.forCsv(), false); // without headers
    formatter.format(
        fieldsWithCustomFormat,
        List.of("count", "value"), // Only include count and value
        Stream.of(data),
        sb);

    // Should only have count and value columns
    // Note: streaming now always adds trailing newlines for consistency
    var csvValues = sb.toString().trim().split(",");
    assertEquals(2, csvValues.length);
    assertEquals("42", csvValues[0]);
    assertEquals("123.457", csvValues[1]);
  }

  @Test
  void testTextFormatter() {
    var data = createSampleData();
    var fields = createFieldDescriptors();

    // Add a field with custom format hint
    var fieldsWithCustomFormat =
        List.of(
            fields.get(0), // name
            fields.get(1), // count
            fields.get(2).withHint("precision", 1), // value with 1 decimal place
            fields.get(3).withHint("pattern", "HH:mm:ss") // timestamp with time only
            );

    var sb = new StringBuilder();
    var formatter = new SimpleTextFormatter<SampleData>(SerializerRegistry.forText());

    // Test with all fields
    formatter.format(
        fieldsWithCustomFormat,
        List.of("name", "count", "value", "timestamp"),
        Stream.of(data),
        sb);

    // Verify text output format and values
    var output = sb.toString();
    assertTrue(output.contains("name     : Test Item"));
    assertTrue(output.contains("count    : 42"));
    assertTrue(output.contains("value    : 123.5")); // 1 decimal place
    assertTrue(output.contains("timestamp: 12:34:56")); // Time only format

    // Test with subset of fields
    sb = new StringBuilder();
    formatter.format(
        fieldsWithCustomFormat,
        List.of("name", "timestamp"), // Only include name and timestamp
        Stream.of(data),
        sb);

    output = sb.toString();
    assertTrue(output.contains("name     : Test Item"));
    assertTrue(output.contains("timestamp: 12:34:56"));
    assertFalse(output.contains("count    : 42"));
    assertFalse(output.contains("value    : 123.5"));
  }

  @Test
  void testMultipleRecords() throws IOException {
    var data1 = createSampleData();
    var data2 =
        new SampleData(
            "Another Item", 100, 987.654321, ZonedDateTime.parse("2022-12-31T23:59:59Z"));

    var fields = createFieldDescriptors();

    // JSON formatter with multiple records
    var sb = new StringBuilder();
    var jsonFormatter = new JsonFormatter<SampleData>(SerializerRegistry.forJson());
    jsonFormatter.format(fields, List.of("name", "value"), Stream.of(data1, data2), sb);

    // We should have two JSON objects separated by newline
    var jsonLines = sb.toString().split("\n");
    assertEquals(2, jsonLines.length);

    var json1 = JsonParser.parseString(jsonLines[0]).getAsJsonObject();
    var json2 = JsonParser.parseString(jsonLines[1]).getAsJsonObject();

    assertEquals("Test Item", json1.get("name").getAsString());
    assertEquals("Another Item", json2.get("name").getAsString());

    // CSV formatter with multiple records
    sb = new StringBuilder();
    var csvFormatter = new CsvFormatter<SampleData>(SerializerRegistry.forCsv(), true);
    csvFormatter.format(fields, List.of("name", "count", "value"), Stream.of(data1, data2), sb);

    var csvParser =
        CSVParser.parse(
            sb.toString(), CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get());

    var records = csvParser.getRecords();
    assertEquals(2, records.size());
    assertEquals("Test Item", records.get(0).get("name"));
    assertEquals("Another Item", records.get(1).get("name"));
  }

  @Test
  void testCsvEscaping() throws IOException {
    record QuoteData(String text) {}

    var data = new QuoteData("value, with \"quotes\"");
    var field = new FieldDescriptor<QuoteData>("text", QuoteData::text);

    var sb = new StringBuilder();
    var formatter = new CsvFormatter<QuoteData>(SerializerRegistry.forCsv(), true);
    formatter.format(List.of(field), List.of("text"), Stream.of(data), sb);

    var expected = "text\r\n\"value, with \"\"quotes\"\"\"\r\n";
    assertEquals(expected, sb.toString());

    var csvParser =
        CSVParser.parse(
            sb.toString(), CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get());
    var record = csvParser.getRecords().getFirst();
    assertEquals("value, with \"quotes\"", record.get("text"));
  }
}
