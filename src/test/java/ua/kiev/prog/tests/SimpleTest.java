package ua.kiev.prog.tests;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ua.kiev.prog.simple.Client;
import ua.kiev.prog.simple.CustomClient;

import javax.persistence.*;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;

import static org.junit.Assert.*;

public class SimpleTest extends BaseTest {

    private Client saveTestClient(final String name, final Integer age) {
        return performTransaction(new Callable<Client>() {
            public Client call() throws Exception {
                Client client = new Client(name, age);
                em.persist(client);//сохранить в базу
                return client;
            }
        });
    }

    @Test()
    public void testPersistAndFind() {
        Client client = saveTestClient("Nikolay", 20);//метод который проверяет тестирует врамках транз сохр его и возвр ссылку

        long id = client.getId();
        assertTrue(id > 0);

        // find existing
        Client other = em.find(Client.class, id);//сохранили теперь ищем его типо проверяем
        assertNotNull(other);//не ноль обьект?
        assertEquals(client.getName(), other.getName());//проверяем равенство сравниваем с тем что ложили и что вытащили
        assertEquals(client.getAge(), other.getAge());

        // clear context
        em.clear();//для чистоты эксперемнета дЕТачт

        // entity was already loaded by find()
        other = em.getReference(Client.class, id);//и снова вытаск клиента как и файнд(кот делает селект запрос)
        //но рефер возвращает обьект обьертку где инициализир только айди/ обьект не подгруж полностью а только айди
        assertNotNull(other);//закеширован обьект по этому не пишет запрос
        assertEquals(client.getName(), other.getName());
        assertEquals(client.getAge(), other.getAge());
    }

    @Test(expected = RuntimeException.class)//должен законч ок ибо ексепшн ожидаем он может быть потому что мы прописали что каолонка возраста должна быть не ноль стопро
    public void testNullable() {
        saveTestClient("Nikolay", null);
    }//проверяет чи возраст не ноль

    @Test
    public void testMerge() {//как персист но это инсерт ОР апдейт
        final Client client = saveTestClient("Ivan", 10);//сохраняем
        long id = client.getId();//запоминаем

        performTransaction(new Callable<Void>() {
            public Void call() throws Exception {//внутри танзакции его модифицируем
                client.setAge(50);
                em.merge(client);//делает его апдейт ибо он уже инсерт он аптдейтит поле возраста
                return null;//если мы явно создали бы его то он его инсерт
            }
        });

        em.clear();//чистим

        Client other = em.find(Client.class, id);//проверяем опять какой же возраст
        assertEquals(50, other.getAge());
    }

    @Test
    public void testRemove() {//всегда должен выполнятся
        final Client client = saveTestClient("Ivan", 10);
        final long id = client.getId();

        performTransaction(new Callable<Void>() {
            public Void call() throws Exception {
                Client other = em.getReference(Client.class, id);//подтягиваем обьект по айди
                em.remove(other);//и удаляем
                return null;
            }
        });

        Client another = em.find(Client.class, id);//вытаскиваем его но его должно не быть
        assertNull(another);
    }

    @Test
    public void testSelect() {
        performTransaction(new Callable<Void>() {
            public Void call() throws Exception {
                for (int i = 0; i < 20; i++) {
                    Client client = new Client("Name" + i, 100 + i);
                    em.persist(client);//генерю обьекты и сохраняю в рамках 1 транзакции
                }
                return null;
            }
        });

        List<Client> resultList;//далее разные выборки
        //Client.c - это ккк фор ич нужны все клиенты где значение равно больше меньше то то то а вот : это типа ? в сикюель
//куери это стетмент типо
        Query query = em.createQuery("SELECT c FROM Client c WHERE c.age >= :age");//метод кот - созд джпикьюел запрос
        query.setParameter("age", 100);//дай мен обьекты где поля равны тому то то
        resultList = (List<Client>) query.getResultList(); // type cast!!! возврашает реультат селекта в кач листа
        assertEquals(20, resultList.size());

        TypedQuery<Client> otherQuery = em.createQuery("SELECT c FROM Client c WHERE c.age >= :age", Client.class);//тут в конце указ тип что будет возвращаеться
        otherQuery.setParameter("age", 100);//тайпквери это интерфейс в дженерике указ тип обьекта с которым работает куери
        resultList = otherQuery.getResultList(); // no type cast
        assertEquals(20, resultList.size());

        TypedQuery<Long> countQuery = em.createQuery("SELECT COUNT(c) FROM Client c WHERE c.age >= :age", Long.class);//подщет клиентов-возвращает количесвто где возраст равен :ейдж
        countQuery.setParameter("age", 100);
        long count = countQuery.getSingleResult();//возвращаеться один обьект - количесвто
        assertEquals(20, count);

        // select properties
        TypedQuery<CustomClient> propQuery = em//селект отдельных полей
                .createQuery("SELECT NEW ua.kiev.prog.simple.CustomClient(c.name, c.age) FROM Client c WHERE c.id = 1",
                        CustomClient.class);//через отдельный новый класс проверять или просто доставать какието поля
        CustomClient res = propQuery.getSingleResult();
        assertNotNull(res);

        TypedQuery<Object[]> otherPropQuery = em
                .createQuery("SELECT c.name, c.age FROM Client c WHERE c.id = 1", Object[].class);//без создавания нового клиента
        Object[] props = otherPropQuery.getSingleResult();//вернет лист масивов обьектов полей класса
        assertEquals(2, props.length);
        assertTrue(props[0] instanceof String);
        assertTrue(props[1] instanceof Integer);
    }

    @Test
    public void testSelectWithLimit() {
        performTransaction(new Callable<Void>() {
            public Void call() throws Exception {
                for (int i = 0; i < 100; i++) {//загнать клиентов
                    Client client = new Client("Anybody" + i, 100 + i);
                    em.persist(client);
                }
                return null;
            }
        });

        TypedQuery<Client> query = em.createNamedQuery("selectByNameLike", Client.class);//именнованный запрос ибо аннотация есть над классом
        query.setParameter("pattern", "%body%");//патерн параметр запроса а бади ищу всех клиентов где есть подстрака бади
        query.setFirstResult(5);//задаю параметры по странично - индекс первого
        query.setMaxResults(20);//индекс последнего клиента из списка который селектица по запросу ----- данные дастают порционно

        List<Client> result = query.getResultList();//без высше заданых параметрам вытащил бы все все клиентов
        assertEquals(20, result.size());

        Client client = result.get(0);//достать первго попавщ
        assertTrue(client.getName().startsWith("Anybody"));//проверочка есть ли бади
        assertEquals(105, client.getAge()); //ну и возраст мы ж задали возраст от 100
    }

    private void performWrongSelect(int age) {
        performTransaction(new Callable<Void>() {
            public Void call() throws Exception {
                for (int i = 0; i < 5; i++) {
                    Client client = new Client("Hello" + i, 200 + i);
                    em.persist(client);
                }
                return null;
            }
        });

        TypedQuery<Client> query = em.createQuery("SELECT c FROM Client c WHERE c.age > :age", Client.class);
        query.setParameter("age", age);
        Client client = query.getSingleResult();
    }

    @Test(expected = NonUniqueResultException.class)
    public void testWrongCount1() {
        performWrongSelect(200);
    }

    @Test(expected = NoResultException.class)
    public void testWrongCount2() {
        performWrongSelect(1000);
    }
}
