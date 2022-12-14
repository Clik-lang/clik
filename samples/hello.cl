main :: () {
  Component :: union {
    Position :: struct {x: i32, y: i32},
    Velocity :: struct {x: i32, y: i32},
  }
  value :: Position {.x: 1, .y: 2};
  print(value);
}
