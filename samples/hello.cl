main :: () {
  value :~ 0;
  fork 0..10 {
    select {
      value = 10; -> value = value + 1;
    }
  }
  print(value);
}