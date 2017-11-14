package ua.kiev.prog.tests;

import org.junit.Test;
import ua.kiev.prog.one2one.Address;
import ua.kiev.prog.one2one.Client;

import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

class TwoIDs {
    private final long clientId;
    private final long addressId;

    public TwoIDs(long clientId, long addressId) {
        this.clientId = clientId;
        this.addressId = addressId;
    }

    public long getClientId() {
        return clientId;
    }

    public long getAddressId() {
        return addressId;
    }
}

public class OneToOneTest extends BaseTest {
    private TwoIDs saveTestClientWithAddress(
            String name, int age, String country, String city, String street) {
        final Client client = new Client(name, age);
        final Address address = new Address(country, city, street);

        client.setAddress(address);
        address.setClient(client); // !! not necessary in this case

        return performTransaction(new Callable<TwoIDs>() {
            public TwoIDs call() throws Exception {
                em.persist(client);
                return new TwoIDs(client.getId(), address.getId());
            }
        });
    }

    @Test
    public void testPersist() {
        TwoIDs ids = saveTestClientWithAddress("Vsevolod", 31,
                "Ukraine", "Kyiv", "Some street");

        em.clear();

        Client otherClient = em.find(Client.class, ids.getClientId());
        assertNotNull(otherClient);

        Address otherAddress = otherClient.getAddress();
        assertEquals("Some street", otherAddress.getStreet());

        em.clear();

        otherAddress = em.find(Address.class, ids.getAddressId());
        assertNotNull(otherAddress);

        otherClient = otherAddress.getClient();
        assertNotNull(otherClient);
        assertEquals("Vsevolod", otherClient.getName());
    }

    @Test
    public void testRemoveClient() {
        TwoIDs ids = saveTestClientWithAddress("Aleksandr", 35,
                "Ukraine", "Lviv", "Any street");

        em.clear();

        final Client client = em.getReference(Client.class, ids.getClientId());
        assertNotNull(client);

        performTransaction(new Callable<Void>() {
            public Void call() throws Exception {
                em.remove(client);
                return null;
            }
        });

        Address address = em.find(Address.class, ids.getAddressId());
        assertNull(address);
    }

    @Test
    public void testRemoveAddressWrong() {
        TwoIDs ids = saveTestClientWithAddress("Ivan", 25,
                "Ukraine", "Kharkiv", "Street 1");

        em.clear();

        final Address address = em.getReference(Address.class, ids.getAddressId());
        assertNotNull(address);

        performTransaction(new Callable<Void>() {
            public Void call() throws Exception {
                em.remove(address);
                return null;
            }
        });

        em.clear();

        Client client = em.find(Client.class, ids.getClientId());
        assertNotNull(client.getAddress());
    }

    @Test
    public void testRemoveAddressCorrect() {
        TwoIDs ids = saveTestClientWithAddress("Vitaliy", 23,
                "Ukraine", "Dnipro", "Street 3");

        em.clear();

        final Address address = em.getReference(Address.class, ids.getAddressId());
        assertNotNull(address);

        performTransaction(new Callable<Void>() {
            public Void call() throws Exception {
                Client client = address.getClient();
                client.setAddress(null);

                em.merge(client);
                em.remove(address);

                return null;
            }
        });

        em.clear();

        Client client = em.find(Client.class, ids.getClientId());
        assertNull(client.getAddress());
    }
}
