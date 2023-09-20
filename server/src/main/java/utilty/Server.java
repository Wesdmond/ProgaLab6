package utilty;

import dtp.Request;
import dtp.Response;
import exceptions.ConnectionErrorException;
import exceptions.OpeningServerException;
import exceptions.ReadingErrorException;
import managers.FileManager;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;


public class Server {
    private int port;
    private Printable console;
    private ServerSocketChannel ss;
    private Selector selector;
    private SelectionKey selectionKey;
    private SocketChannel socketChannel;
    private RequestHandler requestHandler;
    private Request userRequest;
    private Response responseToUser;


    BufferedInputStream bf = new BufferedInputStream(System.in);

    BufferedReader scanner = new BufferedReader(new InputStreamReader(bf));
    private FileManager fileManager;

    public Server(int port, RequestHandler handler, FileManager fileManager) {
        this.port = port;
        this.console = new Console();
        this.requestHandler = handler;
        this.fileManager = fileManager;
    }

    public void run(){
        try{
            openServerSocket();
            while (true) {
                try {
                    if (scanner.ready()) {
                        String line = scanner.readLine();
                        if (line.equals("save") || line.equals("s")) {
                            fileManager.saveObjects();
                        }
                    }
                } catch (IOException ignored) {}
                try {
                    int readyChannels = selector.selectNow();
                    if (readyChannels == 0) continue;
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        if (!key.isValid()) continue;
                        if (key.isAcceptable()) {
                            Accept(key);
                        } else if (key.isReadable()) {
                            // канал готов к чтению
                            Read(key);
                        } else if (key.isWritable()) {
                            // канал готов к чтению
                            Write(key);
                        }
                        keyIterator.remove();
                    }
                } catch (ConnectionErrorException | SocketTimeoutException | ReadingErrorException ignored) {
                } catch (IOException exception) {
                    console.printError("Произошла ошибка при попытке завершить соединение с клиентом!");
                }
            }
        } catch (OpeningServerException e) {
            console.printError("Сервер не может быть запущен");
        }
    }

    private void openServerSocket() throws OpeningServerException{
        try {
            SocketAddress socketAddress = new InetSocketAddress(port);
            ss = ServerSocketChannel.open();
            ss.configureBlocking(false);
            ss.bind(socketAddress);
            selector = Selector.open();
            selectionKey = ss.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IllegalArgumentException exception) {
            console.printError("Порт '" + port + "' находится за пределами возможных значений!");
            throw new OpeningServerException();
        } catch (IOException exception) {
            console.printError("Произошла ошибка при попытке использовать порт '" + port + "'!");
            throw new OpeningServerException();
        }
    }


    private void Accept(SelectionKey key) throws ConnectionErrorException {
        try {
            var ssc = (ServerSocketChannel) key.channel();
            var sc = ssc.accept();
            console.println("Соединение с клиентом успешно установлено.");
            sc.configureBlocking(false);
            sc.register(key.selector(), SelectionKey.OP_READ);
        } catch (IOException e) {
            throw new ConnectionErrorException();
        }

    }

    private void Read(SelectionKey key) throws IOException, ReadingErrorException {
        try {
            SocketChannel sc = (SocketChannel) key.channel();
            ByteBuffer buffer = ByteBuffer.allocate(1024);

            // Читаем данные из сокета в буфер
            int bytesRead;
            ByteArrayOutputStream receivedData = new ByteArrayOutputStream();
            while ((bytesRead = sc.read(buffer)) != -1) {
                if (bytesRead == 0) break;
                buffer.flip(); // Переключаем буфер в режим чтения
                // Записываем данные из буфера во временное хранилище
                receivedData.write(buffer.array(), 0, bytesRead);
                buffer.clear(); // Очищаем буфер для следующего чтения
            }

            // Десериализуем объект из полученных данных
            byte[] receivedBytes = receivedData.toByteArray();
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(receivedBytes);
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
            Request request = (Request) objectInputStream.readObject();
            userRequest = request;
            responseToUser = requestHandler.handle(userRequest);
            sc.register(key.selector(), SelectionKey.OP_WRITE);
        } catch (IOException | ClassNotFoundException e) {
            console.printError(e.getMessage());
            throw new ReadingErrorException();
        }

    }
    private void Write(SelectionKey key) throws IOException {
        try {
            SocketChannel sc = (SocketChannel) key.channel();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);

            // Сериализуем объект в байты
            objectOutputStream.writeObject(responseToUser);
            objectOutputStream.flush();

            // Получаем байты объекта
            byte[] objectBytes = byteArrayOutputStream.toByteArray();
            ByteBuffer buffer = ByteBuffer.wrap(objectBytes);

            while (buffer.hasRemaining()) {
                int bytesWritten = sc.write(buffer);
                if (bytesWritten == 0) break;
            }

            // Закрываем обработку пользователя
            sc.close();
            key.cancel();
        } catch (IOException e) {
            console.printError(e.getMessage());
            throw new IOException();
        }
    }


    private void stop() {
        class ClosingSocketException extends Exception{}
            try{
                if (socketChannel == null) throw new ClosingSocketException();
                socketChannel.close();
                ss.close();
            } catch (ClosingSocketException exception) {
                console.printError("Невозможно завершить работу еще не запущенного сервера!");
            } catch (IOException exception) {
                    console.printError("Произошла ошибка при завершении работы сервера!");
            }
    }
}
