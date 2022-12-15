main :: () {
  value :~ 0;
  fork i: 0..2 {
    if i == 1 {
      break;
    }
    value = value + 1;
  }
  print(value);
}