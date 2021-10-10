package com.github.hashicraft.stateful.blocks;

import java.math.BigInteger;

public class MyType {
  public class MyType2 {
    public String stringType = "mytype2";
    public int intValue = 2;
  }

  public String stringType = "mytype";
  public int intValue = 1;
  public BigInteger bigIntegerValue = new BigInteger("2");
  public MyType2 classValue = new MyType2();
}
