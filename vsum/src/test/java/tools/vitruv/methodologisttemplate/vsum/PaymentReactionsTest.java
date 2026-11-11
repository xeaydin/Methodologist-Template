package tools.vitruv.methodologisttemplate.vsum;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import mir.reactions.modelPayment2Model2.ModelPayment2Model2ChangePropagationSpecification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.change.propagation.ChangePropagationMode;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import tools.vitruv.methodologisttemplate.model.model2.Entity;
import tools.vitruv.methodologisttemplate.model.model2.Link;
import tools.vitruv.methodologisttemplate.model.model2.Root;
import tools.vitruv.methodologisttemplate.model.modelPayment.Customer;
import tools.vitruv.methodologisttemplate.model.modelPayment.ModelPaymentFactory;
import tools.vitruv.methodologisttemplate.model.modelPayment.Order;
import tools.vitruv.methodologisttemplate.model.modelPayment.Payment;
import tools.vitruv.methodologisttemplate.model.modelPayment.PaymentDomain;
import tools.vitruv.methodologisttemplate.model.modelPayment.PaymentProvider;

/**
 * Tests the reactions in {@code paymentReactions.reactions}, which keep {@code model2} consistent
 * with {@code modelPayment}. {@code modelPayment::Entity} (super class of Order, Customer and
 * Payment) is the concept shared with {@code model2::Entity}.
 */
public class PaymentReactionsTest {

  private static final ModelPaymentFactory FACTORY = ModelPaymentFactory.eINSTANCE;

  @BeforeAll
  static void setup() {
    Resource.Factory.Registry.INSTANCE
        .getExtensionToFactoryMap()
        .put("*", new XMIResourceFactoryImpl());
  }

  // ===================== PaymentDomain -> Root =====================

  @Test
  void domainInsertionCreatesRoot(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    Assertions.assertEquals(1, view(vsum).getRootObjects(PaymentDomain.class).size());
    Assertions.assertEquals(1, view(vsum).getRootObjects(Root.class).size());
  }

  @Test
  void reloadFilledVirtualModel(@TempDir Path tempDir) throws IOException {
    InternalVirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    addCustomer(vsum, "Alice");
    vsum.dispose();
    vsum = createVirtualModel(tempDir);
    Assertions.assertEquals(1, view(vsum).getRootObjects(PaymentDomain.class).size());
    Assertions.assertEquals(Set.of("Alice"), entityNames(vsum));
  }

  // ===================== Entity (Customer, Order, Payment) -> model2::Entity =====================

  @Test
  void customerInsertionCreatesEntityWithSameName(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    addCustomer(vsum, "Alice");
    Assertions.assertEquals(Set.of("Alice"), entityNames(vsum));
  }

  @Test
  void orderInsertionCreatesEntityWithSameName(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    addOrder(vsum, "Order 1");
    Assertions.assertEquals(Set.of("Order 1"), entityNames(vsum));
  }

  @Test
  void paymentInsertionCreatesEntityWithSameName(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    addOrder(vsum, "Order 1");
    addPayment(vsum, "Payment 1");
    Assertions.assertEquals(Set.of("Order 1", "Payment 1"), entityNames(vsum));
  }

  @Test
  void providerInsertionCreatesEntityWithSameName(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    addProvider(vsum, "Stripe");
    Assertions.assertEquals(Set.of("Stripe"), entityNames(vsum));
  }

  @Test
  void renamingCustomerRenamesEntity(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    addCustomer(vsum, "Alice");
    modifyDomain(vsum, d -> d.getCustomers().get(0).setName("Bob"));
    Assertions.assertEquals(Set.of("Bob"), entityNames(vsum));
  }

  @Test
  void renamingOrderRenamesEntity(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    addOrder(vsum, "Order 1");
    modifyDomain(vsum, d -> d.getOrders().get(0).setName("Order 2"));
    Assertions.assertEquals(Set.of("Order 2"), entityNames(vsum));
  }

  @Test
  void renamingPaymentRenamesEntity(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    addOrder(vsum, "Order 1");
    addPayment(vsum, "Payment 1");
    modifyDomain(vsum, d -> d.getOrders().get(0).getPayments().get(0).setName("Payment 2"));
    Assertions.assertEquals(Set.of("Order 1", "Payment 2"), entityNames(vsum));
  }

  @Test
  void renamingProviderRenamesEntity(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    addProvider(vsum, "Stripe");
    modifyDomain(vsum, d -> d.getProviders().get(0).setName("Adyen"));
    Assertions.assertEquals(Set.of("Adyen"), entityNames(vsum));
  }

  @Test
  void deletingCustomerDeletesEntity(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    addCustomer(vsum, "Alice");
    addCustomer(vsum, "Bob");
    modifyDomain(vsum, d -> d.getCustomers().remove(0));
    Assertions.assertEquals(Set.of("Bob"), entityNames(vsum));
  }

  @Test
  void deletingProviderDeletesEntity(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    addProvider(vsum, "Stripe");
    modifyDomain(vsum, d -> d.getProviders().remove(0));
    Assertions.assertTrue(entityNames(vsum).isEmpty());
  }

  // ===================== Order.customer -> Link =====================

  @Test
  void settingOrderCustomerCreatesLink(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    addCustomer(vsum, "Alice");
    addOrder(vsum, "Order 1");
    Assertions.assertTrue(links(vsum).isEmpty(), "no customer set yet, so no link expected");

    modifyDomain(vsum, d -> d.getOrders().get(0).setCustomer(d.getCustomers().get(0)));

    var links = links(vsum);
    Assertions.assertEquals(1, links.size());
    Assertions.assertEquals(Set.of("Alice", "Order 1"), linkedEntityNames(links.get(0)));
  }

  @Test
  void changingOrderCustomerReplacesLink(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    addCustomer(vsum, "Alice");
    addCustomer(vsum, "Bob");
    addOrder(vsum, "Order 1");
    modifyDomain(vsum, d -> d.getOrders().get(0).setCustomer(d.getCustomers().get(0)));
    modifyDomain(vsum, d -> d.getOrders().get(0).setCustomer(d.getCustomers().get(1)));

    var links = links(vsum);
    Assertions.assertEquals(1, links.size());
    Assertions.assertEquals(Set.of("Bob", "Order 1"), linkedEntityNames(links.get(0)));
  }

  @Test
  void deletingOrderRemovesCustomerLink(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    addCustomer(vsum, "Alice");
    addOrder(vsum, "Order 1");
    modifyDomain(vsum, d -> d.getOrders().get(0).setCustomer(d.getCustomers().get(0)));
    modifyDomain(vsum, d -> d.getOrders().remove(0));

    Assertions.assertTrue(links(vsum).isEmpty());
    Assertions.assertEquals(Set.of("Alice"), entityNames(vsum));
  }

  // ===================== Payment.provider / Payment.method -> Link =====================

  @Test
  void paymentWithProviderCreatesLinkWithMethodStandard(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    setUpOrderWithProviderAndCard(vsum, tempDir);
    addPaymentWithProviderAndCard(vsum, "Payment 1");

    var links = links(vsum);
    Assertions.assertEquals(1, links.size());
    Assertions.assertEquals(Set.of("Payment 1", "Stripe"), linkedEntityNames(links.get(0)));
    Assertions.assertNotNull(links.get(0).getStandard());
    Assertions.assertEquals("CreditCard", links.get(0).getStandard().getName());
  }

  @Test
  void paymentWithoutProviderCreatesNoLink(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    addOrder(vsum, "Order 1");
    addPayment(vsum, "Payment 1");
    Assertions.assertTrue(links(vsum).isEmpty());
  }

  @Test
  void settingProviderOnExistingPaymentCreatesLink(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    setUpOrderWithProviderAndCard(vsum, tempDir);
    addPayment(vsum, "Payment 1");
    Assertions.assertTrue(links(vsum).isEmpty());

    modifyDomain(
        vsum,
        d -> d.getOrders().get(0).getPayments().get(0).setProvider(d.getProviders().get(0)));

    var links = links(vsum);
    Assertions.assertEquals(1, links.size());
    Assertions.assertEquals(Set.of("Payment 1", "Stripe"), linkedEntityNames(links.get(0)));
  }

  @Test
  void deletingPaymentRemovesEntityAndLink(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    setUpOrderWithProviderAndCard(vsum, tempDir);
    addPaymentWithProviderAndCard(vsum, "Payment 1");
    modifyDomain(vsum, d -> d.getOrders().get(0).getPayments().remove(0));

    Assertions.assertTrue(links(vsum).isEmpty());
    Assertions.assertEquals(Set.of("Order 1", "Stripe", "Alice"), entityNames(vsum));
  }

  // ===================== helpers =====================

  /** Domain with customer "Alice" (owning a credit card), provider "Stripe" and "Order 1". */
  private void setUpOrderWithProviderAndCard(VirtualModel vsum, Path tempDir) {
    addDomain(vsum, tempDir);
    modifyDomain(
        vsum,
        d -> {
          var customer = FACTORY.createCustomer();
          customer.setName("Alice");
          customer.getPaymentMethods().add(FACTORY.createCreditCard());
          d.getCustomers().add(customer);
          var provider = FACTORY.createPaymentProvider();
          provider.setName("Stripe");
          d.getProviders().add(provider);
        });
    addOrder(vsum, "Order 1");
  }

  private void addPaymentWithProviderAndCard(VirtualModel vsum, String name) {
    modifyDomain(
        vsum,
        d -> {
          var payment = FACTORY.createPayment();
          payment.setName(name);
          payment.setProvider(d.getProviders().get(0));
          payment.setMethod(d.getCustomers().get(0).getPaymentMethods().get(0));
          d.getOrders().get(0).getPayments().add(payment);
        });
  }

  private void addDomain(VirtualModel vsum, Path projectPath) {
    var view = view(vsum).withChangeDerivingTrait();
    view.registerRoot(
        FACTORY.createPaymentDomain(),
        URI.createFileURI(projectPath.toString() + "/example.modelpayment"));
    view.commitChanges();
  }

  private void addCustomer(VirtualModel vsum, String name) {
    modifyDomain(
        vsum,
        d -> {
          Customer customer = FACTORY.createCustomer();
          customer.setName(name);
          d.getCustomers().add(customer);
        });
  }

  private void addProvider(VirtualModel vsum, String name) {
    modifyDomain(
        vsum,
        d -> {
          PaymentProvider provider = FACTORY.createPaymentProvider();
          provider.setName(name);
          d.getProviders().add(provider);
        });
  }

  private void addOrder(VirtualModel vsum, String name) {
    modifyDomain(
        vsum,
        d -> {
          Order order = FACTORY.createOrder();
          order.setName(name);
          d.getOrders().add(order);
        });
  }

  /** Adds a payment without provider or method to the first order. */
  private void addPayment(VirtualModel vsum, String name) {
    modifyDomain(
        vsum,
        d -> {
          Payment payment = FACTORY.createPayment();
          payment.setName(name);
          d.getOrders().get(0).getPayments().add(payment);
        });
  }

  private void modifyDomain(VirtualModel vsum, Consumer<PaymentDomain> modification) {
    var view = view(vsum).withChangeDerivingTrait();
    modification.accept(view.getRootObjects(PaymentDomain.class).iterator().next());
    view.commitChanges();
  }

  private Set<String> entityNames(VirtualModel vsum) {
    return view(vsum).getRootObjects(Root.class).iterator().next().getEntities().stream()
        .map(Entity::getName)
        .collect(Collectors.toSet());
  }

  private List<Link> links(VirtualModel vsum) {
    return view(vsum).getRootObjects(Root.class).iterator().next().getLinks();
  }

  private Set<String> linkedEntityNames(Link link) {
    return link.getEntities().stream().map(Entity::getName).collect(Collectors.toSet());
  }

  private InternalVirtualModel createVirtualModel(Path projectPath) throws IOException {
    InternalVirtualModel model =
        new VirtualModelBuilder()
            .withStorageFolder(projectPath)
            .withUserInteractorForResultProvider(
                new TestUserInteraction.ResultProvider(new TestUserInteraction()))
            .withChangePropagationSpecifications(
                new ModelPayment2Model2ChangePropagationSpecification())
            .buildAndInitialize();
    model.setChangePropagationMode(ChangePropagationMode.TRANSITIVE_CYCLIC);
    return model;
  }

  private View view(VirtualModel vsum) {
    return createView(vsum, List.of(PaymentDomain.class, Root.class));
  }

  // See https://github.com/vitruv-tools/Vitruv/issues/717 for more information about the rootTypes
  private View createView(VirtualModel vsum, Collection<Class<?>> rootTypes) {
    var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("default"));
    selector.getSelectableElements().stream()
        .filter(element -> rootTypes.stream().anyMatch(it -> it.isInstance(element)))
        .forEach(it -> selector.setSelected(it, true));
    return selector.createView();
  }
}
