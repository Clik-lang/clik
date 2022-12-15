main :: () {
  value := 5;
  select {
    value = 10; {}
    #sleep(3000); {}
  }
  print(value);
}