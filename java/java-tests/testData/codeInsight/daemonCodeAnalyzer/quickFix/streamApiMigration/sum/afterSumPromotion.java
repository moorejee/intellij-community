// "Replace with sum()" "true-preview"

import java.util.List;

public class Main {
  public void testPrimitiveMap(List<String> data) {
    long sum = data.stream().filter(str -> str.startsWith("xyz")).mapToInt(String::length).filter(len -> len > 10).asLongStream().sum();
  }
}