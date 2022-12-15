 main :: () {
  value :~ 0;
  fork i: 0..2 {
    if i == 1 {
      value = 1;
      #sleep(1000);
      break;
    }
    if value == 1 {
      print("value is 1");
      break;
    }
    select {
      test :: $value; -> value = test + 5;
    }
  }
  print(value);
 }