package com.github.hashicraft.stateful.blocks;

public class MyType {
  public class MyType2 {
    public String stringType = "mytype2";
    public int intValue = 2;
  }

  public String stringType = "mytype";
  public int intValue = 1;
  public MyType2 classValue = new MyType2();
}
