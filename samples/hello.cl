Point :: struct {x: i32, y: i32}
main :: () {
  Component :: enum Point {
    Position :: {.x: 1, .y: 2},
    Velocity :: {.x: 3, .y: 4},
  }
  print(Component.Velocity);
}
