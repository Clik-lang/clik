main :: () {
  server :: open_server(8080);
  print("Server started on port 8080");
  for {
    client :: accept_client(server);
    spawn {
      send(client, "HTTP/1.1 200 OK\r\n\r\nHello World");
      close(client);
    }
  }
}
