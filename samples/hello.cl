Component :: union {
    Position :: struct {x: i32, y: i32},
    Velocity :: struct {x: i32, y: i32},
}

main :: () {
  value :: get_component();
  print(value);
}

get_component :: () Component {
  return Position {1, 2};
}
