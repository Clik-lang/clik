main :: () int {
  add :: (a: int, b: int) int -> a + b;
  forward :: (a: int, b: int, function: (c: int, d: int) int) int -> function(a, b);
  value :: forward(5, 6, add);
  return value;
}