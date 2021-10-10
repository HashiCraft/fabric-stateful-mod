package com.github.hashicraft.stateful.blocks;

import java.io.IOException;
import java.math.BigInteger;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class BigIntegerTypeAdapter extends TypeAdapter<BigInteger> {

  @Override
  public void write(JsonWriter out, BigInteger value) throws IOException {
    // TODO Auto-generated method stub
    out.value(value.toString());
  }

  @Override
  public BigInteger read(JsonReader in) throws IOException {
    // TODO Auto-generated method stub
    String value = in.nextString();
    return new BigInteger(value);
  }

}
