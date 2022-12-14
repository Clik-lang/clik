Point :: struct {x: i32, y: i32}
main :: () {
  values :: map[Point]i32 {{1, 2}: 5};
  print( values[{1,2}]);
}
