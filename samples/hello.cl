main :: () {
  Point :: struct {x: i32, y: i32}
  array :: []Point {{1,2}, {3,4}, {5,6}}
  value := 0;
  for .x: array -> value = value + x;
  print(value);
}