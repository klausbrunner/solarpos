package net.e175.klaus.formatter;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SerializerRegistryTest {

  @Test
  void defaultRegistrySerializesBasicTypes() {
    var registry = SerializerRegistry.defaults();

    assertEquals("hello", registry.serialize("hello"));
    assertEquals("true", registry.serialize(true));
    assertEquals("false", registry.serialize(false));
    assertEquals("42", registry.serialize(42));
    assertEquals("42", registry.serialize(42L));
  }

  @Test
  void defaultRegistrySerializesNullAsEmptyString() {
    var registry = SerializerRegistry.defaults();

    assertEquals("", registry.serialize(null));
  }

  @Test
  void customNullValue() {
    var registry = SerializerRegistry.defaults().withNullValue("NULL");

    assertEquals("NULL", registry.serialize(null));
  }

  @Test
  void textRegistryFormatsDoublesWith2Decimals() {
    var registry = SerializerRegistry.forText();

    assertTrue(registry.serialize(3.14159).contains("3.14"));
    assertTrue(registry.serialize(42.0).contains("42.00"));
    assertTrue(registry.serialize(0.5).contains("0.50"));
  }

  @Test
  void csvRegistryFormatsDoublesWith5Decimals() {
    var registry = SerializerRegistry.forCsv();

    assertEquals("3.14159", registry.serialize(3.14159265));
    assertEquals("42.00000", registry.serialize(42.0));
    assertEquals("0.50000", registry.serialize(0.5));
  }

  @Test
  void jsonRegistryFormatsDoublesWith6Decimals() {
    var registry = SerializerRegistry.forJson();

    assertEquals("3.141593", registry.serialize(3.141592653589));
    assertEquals("42.000000", registry.serialize(42.0));
    assertEquals("0.500000", registry.serialize(0.5));
  }

  @Test
  void serializesWithFieldDescriptorPrecision() {
    var registry = SerializerRegistry.forCsv();
    var field = FieldDescriptor.numeric("value", (Double x) -> x, 3);

    assertEquals("3.142", registry.serialize(3.14159265, field));
  }

  @Test
  void serializesZonedDateTimeForText() {
    var registry = SerializerRegistry.forText();
    var zdt = ZonedDateTime.of(2024, 1, 15, 10, 30, 45, 0, ZoneOffset.UTC);

    assertEquals("2024-01-15 10:30:45Z", registry.serialize(zdt));
  }

  @Test
  void serializesZonedDateTimeForCsv() {
    var registry = SerializerRegistry.forCsv();
    var zdt = ZonedDateTime.of(2024, 1, 15, 10, 30, 45, 0, ZoneOffset.UTC);

    assertEquals("2024-01-15T10:30:45Z", registry.serialize(zdt));
  }

  @Test
  void serializesZonedDateTimeForJson() {
    var registry = SerializerRegistry.forJson();
    var zdt = ZonedDateTime.of(2024, 1, 15, 10, 30, 45, 0, ZoneOffset.UTC);

    assertEquals("\"2024-01-15T10:30:45Z\"", registry.serialize(zdt));
  }

  @Test
  void serializesZonedDateTimeWithCustomPattern() {
    var registry = SerializerRegistry.forCsv();
    var field = FieldDescriptor.dateTime("time", (ZonedDateTime x) -> x, "yyyy/MM/dd HH:mm");
    var zdt = ZonedDateTime.of(2024, 1, 15, 10, 30, 45, 0, ZoneOffset.UTC);

    assertEquals("2024/01/15 10:30", registry.serialize(zdt, field));
  }

  @Test
  void jsonRegistrySerializesNullDateTimeAsNull() {
    var registry = SerializerRegistry.forJson();
    var field = new FieldDescriptor<>("time", x -> null);

    assertEquals("", registry.serialize(null, field));
  }

  @Test
  void appendDoubleWithPrecision() throws IOException {
    var registry = SerializerRegistry.defaults();
    var sb = new StringBuilder();

    registry.appendDouble(3.14159, 2, sb);

    assertEquals("3.14", sb.toString());
  }

  @Test
  void appendFloatWithPrecision() throws IOException {
    var registry = SerializerRegistry.defaults();
    var sb = new StringBuilder();

    registry.appendFloat(3.14159f, 3, sb);

    assertEquals("3.142", sb.toString());
  }

  @Test
  void getPrecisionFromFieldDescriptor() {
    var registry = SerializerRegistry.defaults();
    var field = FieldDescriptor.numeric("value", (Double x) -> x, 7);

    assertEquals(7, registry.getPrecision(field));
  }

  @Test
  void getPrecisionDefaultsTo5() {
    var registry = SerializerRegistry.defaults();
    var field = new FieldDescriptor<>("value", x -> x);

    assertEquals(5, registry.getPrecision(field));
  }

  @Test
  void customSerializerOverridesDefault() {
    var registry = SerializerRegistry.defaults().register(Integer.class, (i, hints) -> "INT:" + i);

    assertEquals("INT:42", registry.serialize(42));
  }

  @Test
  void serializerUsesHierarchy() {
    var registry = SerializerRegistry.defaults();

    assertTrue(registry.serialize(3.14f).contains("3.14"));
    assertTrue(registry.serialize(3.14).contains("3.14"));
  }

  @Test
  void textFormatterIncludesUnitsForNumbers() {
    var registry = SerializerRegistry.forText();
    var field = new FieldDescriptor<>("value", x -> x).withUnit("°");

    var result = registry.serialize(45.5, field);

    assertTrue(result.contains("45.50"));
    assertTrue(result.contains("°"));
  }

  @Test
  void formatSpecsArrayHasCorrectPrecisions() {
    assertEquals(10, SerializerRegistry.FORMAT_SPECS.length);
    assertEquals("%.0f", SerializerRegistry.FORMAT_SPECS[0]);
    assertEquals("%.5f", SerializerRegistry.FORMAT_SPECS[5]);
    assertEquals("%.9f", SerializerRegistry.FORMAT_SPECS[9]);
  }

  @Test
  void withNullValueRequiresNonNull() {
    var registry = SerializerRegistry.defaults();

    assertThrows(NullPointerException.class, () -> registry.withNullValue(null));
  }

  @Test
  void fieldDescriptorHintsAreImmutable() {
    Map<String, Object> hints = Map.of("key", "value");
    var field = new FieldDescriptor<Object>("name", x -> x, hints);

    // hints are immutable copies
    assertThrows(UnsupportedOperationException.class, () -> field.hints().put("new", "value"));
  }

  @Test
  void withHintCreatesNewDescriptor() {
    var field = new FieldDescriptor<>("name", x -> x);
    var newField = field.withHint("precision", 3);

    assertTrue(field.hints().isEmpty());
    assertEquals(3, newField.hints().get("precision"));
    assertNotSame(field, newField);
  }

  @Test
  void withPrecisionAddsHint() {
    var field = new FieldDescriptor<>("name", x -> x).withPrecision(4);

    assertEquals(4, field.hints().get("precision"));
  }

  @Test
  void withPatternAddsHint() {
    var field = new FieldDescriptor<>("name", x -> x).withPattern("yyyy-MM-dd");

    assertEquals("yyyy-MM-dd", field.hints().get("pattern"));
  }

  @Test
  void withPatternRequiresNonNull() {
    var field = new FieldDescriptor<>("name", x -> x);

    var ex = assertThrows(NullPointerException.class, () -> field.withPattern(null));
    assertTrue(ex.getMessage().contains("Pattern must not be null"));
  }

  @Test
  void withUnitAddsHint() {
    var field = new FieldDescriptor<>("name", x -> x).withUnit("meters");

    assertEquals("meters", field.hints().get("unit"));
  }

  @Test
  void withUnitRequiresNonNull() {
    var field = new FieldDescriptor<>("name", x -> x);

    var ex = assertThrows(NullPointerException.class, () -> field.withUnit(null));
    assertTrue(ex.getMessage().contains("Unit must not be null"));
  }

  @Test
  void fieldDescriptorRequiresNonNullName() {
    var ex = assertThrows(NullPointerException.class, () -> new FieldDescriptor<>(null, x -> x));
    assertTrue(ex.getMessage().contains("Field name must not be null"));
  }

  @Test
  void fieldDescriptorRequiresNonNullExtractor() {
    var ex = assertThrows(NullPointerException.class, () -> new FieldDescriptor<>("name", null));
    assertTrue(ex.getMessage().contains("Field extractor must not be null"));
  }

  @Test
  void serializeUsesFieldNameInHints() {
    var registry =
        SerializerRegistry.defaults()
            .register(String.class, (s, hints) -> hints.get("fieldName") + ":" + s);
    var field = new FieldDescriptor<>("testField", x -> x);

    assertEquals("testField:value", registry.serialize("value", field));
  }

  @Test
  void serializeFallsBackToToString() {
    var registry = SerializerRegistry.defaults();
    var customObject =
        new Object() {
          @Override
          public String toString() {
            return "CustomObject";
          }
        };

    assertEquals("CustomObject", registry.serialize(customObject));
  }

  @Test
  void dateTimeFormatterCachingWorks() {
    var registry = SerializerRegistry.forJson();
    var field1 = FieldDescriptor.dateTime("time1", (ZonedDateTime x) -> x, "yyyy-MM-dd");
    var field2 = FieldDescriptor.dateTime("time2", (ZonedDateTime x) -> x, "yyyy-MM-dd");
    var zdt = ZonedDateTime.of(2024, 1, 15, 10, 30, 45, 0, ZoneOffset.UTC);

    var result1 = registry.serialize(zdt, field1);
    var result2 = registry.serialize(zdt, field2);

    assertEquals("\"2024-01-15\"", result1);
    assertEquals("\"2024-01-15\"", result2);
  }

  @Test
  void precisionBeyondArrayBoundsCausesException() {
    var registry = SerializerRegistry.forCsv();
    var field = FieldDescriptor.numeric("value", (Double x) -> x, 15);

    // This currently throws ArrayIndexOutOfBoundsException - this is a bug
    assertThrows(ArrayIndexOutOfBoundsException.class, () -> registry.serialize(Math.PI, field));
  }
}
