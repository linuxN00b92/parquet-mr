/**
 * Copyright 2012 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package parquet.pig;

import static org.junit.Assert.assertEquals;

import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.apache.pig.impl.util.Utils;
import org.junit.Test;

import parquet.pig.PigSchemaConverter;
import parquet.schema.MessageType;
import parquet.schema.MessageTypeParser;


public class TestPigSchemaConverter {
  
  private final PigSchemaConverter pigSchemaConverter = new PigSchemaConverter();
  
  private void testPigConversion(String pigSchemaString) throws Exception {
    Schema pigSchema = Utils.getSchemaFromString(pigSchemaString);
    MessageType parquetSchema = pigSchemaConverter.convert(pigSchema);
    Schema convertedSchema = pigSchemaConverter.convert(parquetSchema);  
    assertEquals(pigSchema, convertedSchema);
  }
  
  @Test
  public void testSimpleBag() throws Exception {
    testPigConversion("b:{t:(a:int)}");
  }
  
  @Test
  public void testMultiBag() throws Exception {
    testPigConversion("x:int, b:{t:(a:int,b:chararray)}}");
  }
  
  @Test
  public void testMapSimple() throws Exception {
    testPigConversion("b:[(c:int)]");
  }
  
  @Test
  public void testMapTuple() throws Exception {
    testPigConversion("a:chararray, b:[(c:chararray, d:chararray)]");
  }
  
  @Test
  public void testMapOfList() throws Exception {
    testPigConversion("a:map[{bag: (a:int)}]");
  }

  private void testConversion(String pigSchemaString, String schemaString) throws Exception {
    Schema pigSchema = Utils.getSchemaFromString(pigSchemaString);
    MessageType schema = pigSchemaConverter.convert(pigSchema);
    MessageType expectedMT = MessageTypeParser.parseMessageType(schemaString);
    assertEquals("converting "+pigSchemaString+" to "+schemaString, expectedMT, schema);

    MessageType filtered = pigSchemaConverter.filter(schema, pigSchema);
    assertEquals("converting "+pigSchemaString+" to "+schemaString+" and filtering", schema.toString(), filtered.toString());
  }

  @Test
  public void testTupleBag() throws Exception {
    testConversion(
        "a:chararray, b:{t:(c:chararray, d:chararray)}",
        "message pig_schema {\n" +
        "  optional binary a;\n" +
        "  optional group b {\n" +
        "    repeated group t {\n" +
        "      optional binary c;\n" +
        "      optional binary d;\n" +
        "    }\n" +
        "  }\n" +
        "}\n");
  }

  @Test
  public void testTupleBagWithAnonymousInnerField() throws Exception {
    testConversion(
        "a:chararray, b:{(c:chararray, d:chararray)}",
        "message pig_schema {\n" +
        "  optional binary a;\n" +
        "  optional group b {\n" +
        "    repeated group bag {\n" + // the inner field in the bag is called "bag"
        "      optional binary c;\n" +
        "      optional binary d;\n" +
        "    }\n" +
        "  }\n" +
        "}\n");
  }

  @Test
  public void testMap() throws Exception {
    testConversion(
        "a:chararray, b:[(c:chararray, d:chararray)]",
        "message pig_schema {\n" +
        "  optional binary a;\n" +
        "  optional group b {\n" +
        "    repeated group map {\n" +
        "      required binary key;\n" +
        "      optional group value {\n" +
        "        optional binary c;\n" +
        "        optional binary d;\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}\n");
  }

  @Test
  public void testMap2() throws Exception {
    testConversion("a:map[int]",
        "message pig_schema {\n" +
        "  optional group a {\n" +
        "    repeated group map {\n" +
        "      required binary key;\n" +
        "      optional int32 value;" +
        "    }\n" +
        "  }\n" +
        "}\n");
  }

  @Test
  public void testMap3() throws Exception {
    testConversion("a:map[map[int]]",
        "message pig_schema {\n" +
        "  optional group a {\n" +
        "    repeated group map {\n" +
        "      required binary key;\n" +
        "      optional group value {\n" +
        "        repeated group map {\n" +
        "          required binary key;\n" +
        "          optional int32 value;\n" +
        "        }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}\n");
  }

  @Test
  public void testMap4() throws Exception {
    testConversion("a:map[bag{(a:int)}]",
        "message pig_schema {\n" +
        "  optional group a {\n" +
        "    repeated group map {\n" +
        "      required binary key;\n" +
        "      optional group value {\n" +
        "        repeated group bag {\n" +
        "          optional int32 a;\n" +
        "        }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}\n");
  }

  @Test
  public void testAnnonymousField() throws Exception {
    testConversion(
        "a:chararray, int",
        "message pig_schema {\n" +
        "  optional binary a;\n" +
        "  optional int32 val_0;\n" +
        "}\n");
  }
}
