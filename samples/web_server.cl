print :: (text: UTF8);
open_server :: (port: I32) I32;
accept_client :: (server: I32) I32;
send :: (socket: I32, data: UTF8);
close :: (socket: I32);

server :: open_server(I32.8080);
print("Server started on port 8080");
for {
  client :: accept_client(server);
  spawn {
    send(client, "HTTP/1.1 200 OK\r\nContent-Length: 13\r\nConnection: close\r\n\r\nHello, world!");
    close(client);
  }
}
