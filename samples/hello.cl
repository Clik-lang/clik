#load "api.cl";

main :: () {
  print("start");
  stop :~ false;
  spawn {
    stop = true;
  }
  stop = $stop;
  print("end");
}
