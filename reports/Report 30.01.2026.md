# Отчёт о проделанной работе
**Период:** 19 неделя проекта  
**Дата составления:** 30.01.2026  

## Описание проделанной работы

За девятнадцатую неделю разработки были успешно выполнены следующие шаги:
* Изучена документация по libpq (C++ lib to interact with postgres DB)
* Написан пример по взаимодействию с базой данных POSTGRES и ZMQ
* Описаны основные этапы и preparations которые нужно сделать

## Поэтапное описание (основные изменения)

* Написан пример сервера для взаимодействия postgres db и android приложением через ZMQ сокеты.
```c++
    #include <iostream>
    #include <string>
    #include <sstream>

    #include <libpq-fe.h>

    #include <zmq.hpp>

    #define HOST "localhost"
    #define PORT "5432"
    #define DB_NAME "YOUR_DB_NAME"
    #define DB_USER "YOUR_USER"               //по умолчанию postgres
    #define DB_USER_PASSWORD "YOUR_PASSWORD" //Пароль от DB_USER

    #define ZMQ_PORT "YOUR_PORT"

    #define DEBUG 0

    /* Плохая практика использовать using namespace std; вместо этого будем писать std::
    Для того чтобы не вызывать конфликты и не копировать ненужные элементы */

    void AppendData(PGconn *con, const std::string& table, const char **Recvd_values, const int Recvd_size){
                        std::string query =  "INSERT INTO " +
                            table +
                            "(lat, lon, signal_level, capture_time)" +
                            "VALUES ($1, $2, $3, to_timestamp($4))";
                        PGresult* res = PQexecParams(             // передаем параметры отдельно для защиты от SQL injection
                            con,                                  // наше установленное соединение
                            query.c_str(),                        // строка запроса где table - таблица в БД
                            Recvd_size,                           // количество переданных параметров
                            NULL,                                 // типы данных (NULL - автотипизация)
                            Recvd_values,                         // массив с данными
                            NULL,                                 // длины данных
                            NULL,                                 // формат (0 - текст)
                            0                                     // результат текстом
                            );

                            // проверяем успешен ли запрос на добавление в базу
                            if (PQresultStatus(res) != PGRES_COMMAND_OK) {
                                std::cerr << "\033[31mОШИБКА\033[0m:" << PQresultErrorMessage(res) << "\n";
                            } else{
                                std::cout << "Вставка произошла \033[32mУСПЕШНО!\033[0m\n";
                            }

                             PQclear(res);
                    }

    int main(){

            PGconn *con;            // обьект подключения
            PGresult *res;          // результат запроса к базе

            const char* info = "host=" HOST " port=" PORT " dbname=" DB_NAME " user=" DB_USER " password=" DB_USER_PASSWORD;
            con = PQconnectdb(info);                // подключаемся к бд по данным из info

            if (PQstatus(con) != CONNECTION_OK){                      // если подключение не удалось пишем ошибку
                    std::cerr << "\033[31mОШИБКА\033[0m подключения к БД.\n" << PQerrorMessage(con) << "\n";
                    PQfinish(con);                                   // рвём подключение перед выходом
                    exit(1);
            }

            std::cout<< "Подключение \033[32mУСПЕШНО!\033[0m\n\n";

            if (DEBUG){
                const char* test_data[] = {"12.345", "67.891", "4", "1769969928"};
                AppendData(con, "data", test_data, 4);
            }

            zmq::context_t context(1);
            zmq::socket_t socket(context, zmq::socket_type::rep);
            socket.bind( "tcp://*:" ZMQ_PORT );
            std::cout << "ZMQ прослушивает порт: " ZMQ_PORT << "\n";

        while (true) {
          zmq::message_t request;
          auto res_recv = socket.recv(request, zmq::recv_flags::none);

            if (!res_recv) continue;                  // не получилось принять данные - инициируем повтор

            std::string msg = request.to_string();    // парсинг полей из полученных данных
            std::stringstream ss(msg);
            std::string lat, lon, signal, timestamp;

            if (std::getline(ss, lat, ';') &&
                std::getline(ss, lon, ';') &&
                std::getline(ss, signal, ';') &&
                std::getline(ss, timestamp, ';'))
            {

                const char* values[] = { lat.c_str(), lon.c_str(), signal.c_str(), timestamp.c_str() };
                AppendData(con, "data", values, 4);    // отправляем полученные поля в БД если всё ок

                socket.send(zmq::str_buffer("ACK"), zmq::send_flags::none);   // высылаем ACK отправителю
            }
            else{
                std::cerr << "Получены битые данные: " << msg << std::endl;
                socket.send(zmq::str_buffer("ERROR"), zmq::send_flags::none);
              }
        }

            PQfinish(con);        // закрываем успешное подключение
            return 0;
    }
```
* Добавлено описание к коду и подготовке (что нужно установить и создать перед началом работы)
* Поработал с Notifications в проекте с location

## TODO
 - [ ] Привести код в порядок и удалить legacy
 - [ ] Подготовить приложение к релизу
 - [ ] Освежить в памяти теорию