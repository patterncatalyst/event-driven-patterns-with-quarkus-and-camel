package com.patterncatalyst.shipping.evolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;

import org.apache.avro.AvroTypeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.Test;

/** Schema resolution as the Avro specification defines it, with no registry and no service. */
class AvroResolutionTest {

    private static byte[] v1Bytes() throws Exception {
        var out = new ByteArrayOutputStream();
        var encoder = EncoderFactory.get().binaryEncoder(out, null);
        new GenericDatumWriter<GenericRecord>(Registry.v1()).write(Registry.record(Registry.v1(), null), encoder);
        encoder.flush();
        return out.toByteArray();
    }

    private static GenericRecord read(byte[] bytes, Schema reader) throws Exception {
        return new GenericDatumReader<GenericRecord>(Registry.v1(), reader).read(null,
                DecoderFactory.get().binaryDecoder(bytes, null));
    }

    @Test
    void aV2ReaderFillsTheDefaultIntoV1Bytes() throws Exception {
        assertEquals("STANDARD", read(v1Bytes(), Registry.v2()).get("shippingPriority").toString());
    }

    @Test
    void aV3ReaderCannotReadV1BytesBecauseTheFieldHasNoDefault() throws Exception {
        byte[] bytes = v1Bytes();
        var failure = assertThrows(AvroTypeException.class, () -> read(bytes, Registry.v3()));
        assertEquals(true, failure.getMessage().contains("shippingPriority"));
    }
}
