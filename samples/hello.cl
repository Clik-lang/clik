Point :: struct {x: i32, y: i32}
main :: () i32 {
  values :: map[Point]i32 {{1, 2}: 5};
  return values[{1,2}];
}
