main :: () {
  Point :: struct {x: i32, y: i32}
  array :: []Point {{1,2}, {3,4}, {5,6}}
  value := 0;
  for p: array -> value = value + p.x;
  print(value);
}