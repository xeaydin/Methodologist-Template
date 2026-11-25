package tools.vitruv.methodologisttemplate.vsum;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
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
import tools.vitruv.dsls.vitruvocl.pipeline.VitruvOCL;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import tools.vitruv.methodologisttemplate.model.modelPayment.Customer;
import tools.vitruv.methodologisttemplate.model.modelPayment.ModelPaymentFactory;
import tools.vitruv.methodologisttemplate.model.modelPayment.PaymentDomain;

/**
 * Tests the OCL constraints in {@code constraintsPayment.ocl} against a VSUM containing {@code
 * modelPayment} instances.
 */
public class VSUMPaymentTest {

  private static final ModelPaymentFactory FACTORY = ModelPaymentFactory.eINSTANCE;

  /** Surefire runs with the {@code vsum} module as working directory, hence the {@code ../}. */
  private static final Path CONSTRAINT_FILE =
      Path.of(
          "../consistency/src/main/constraints/tools/vitruv/methodologisttemplate/consistency/constraintsPayment.ocl");

  @BeforeAll
  static void setup() {
    Resource.Factory.Registry.INSTANCE
        .getExtensionToFactoryMap()
        .put("*", new XMIResourceFactoryImpl());
  }

  @Test
  void validDomainSatisfiesAllConstraints(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    modifyDomain(
        vsum,
        d -> {
          d.getCustomers().add(customerWithPayPal("Emir", "emir@example.com", "emir@example.com"));
          d.getCustomers().add(customerWithPayPal("Asya", "asya@example.com", "asya@example.com"));
          d.getOrders().add(orderWithInvoice("Order 1", "INV-1"));
          d.getOrders().add(orderWithInvoice("Order 2", "INV-2"));
        });

    var result = evaluate(vsum);
    Assertions.assertTrue(
        result.allSatisfied(),
        () -> "Expected all constraints satisfied but got: " + result.getSummary());
  }

  @Test
  void customerWithoutPaymentMethodViolatesConstraint(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    modifyDomain(
        vsum,
        d -> {
          Customer customer = FACTORY.createCustomer();
          customer.setName("Emir");
          customer.setEmail("Emir@example.com");
          d.getCustomers().add(customer);
        });
    assertViolated(vsum, "CustomerHasPaymentMethod");
  }

  @Test
  void duplicateCustomerEmailViolatesConstraint(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    modifyDomain(
        vsum,
        d -> {
          d.getCustomers().add(customerWithPayPal("Emir", "same@example.com", "same@example.com"));
          d.getCustomers().add(customerWithPayPal("Bob", "same@example.com", "same@example.com"));
        });
    assertViolated(vsum, "CustomerEmailIsUnique");
  }

  @Test
  void missingCustomerEmailViolatesConstraint(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    modifyDomain(vsum, d -> d.getCustomers().add(customerWithPayPal("Emir", null, "a@example.com")));
    assertViolated(vsum, "CustomerEmailIsUnique");
  }

  @Test
  void duplicateInvoiceNumberViolatesConstraint(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    modifyDomain(
        vsum,
        d -> {
          d.getOrders().add(orderWithInvoice("Order 1", "INV-1"));
          d.getOrders().add(orderWithInvoice("Order 2", "INV-1"));
        });
    assertViolated(vsum, "InvoiceNumberIsUnique");
  }

  @Test
  void payPalEmailMismatchViolatesConstraint(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    modifyDomain(
        vsum,
        d ->
            d.getCustomers()
                .add(customerWithPayPal("Emir", "Emir@example.com", "other@example.com")));
    assertViolated(vsum, "PayPalEmailMatchesCustomerEmail");
  }

  @Test
  void fixingPayPalEmailRestoresConsistency(@TempDir Path tempDir) throws IOException {
    VirtualModel vsum = createVirtualModel(tempDir);
    addDomain(vsum, tempDir);
    modifyDomain(
        vsum,
        d ->
            d.getCustomers()
                .add(customerWithPayPal("Emir", "Emir@example.com", "other@example.com")));
    assertViolated(vsum, "PayPalEmailMatchesCustomerEmail");

    modifyDomain(vsum, d -> d.getCustomers().get(0).setEmail("other@example.com"));
    var result = evaluate(vsum);
    Assertions.assertTrue(result.allSatisfied(), result::getSummary);
  }

  // ===================== helpers =====================

  private Customer customerWithPayPal(String name, String email, String payerEmail) {
    Customer customer = FACTORY.createCustomer();
    customer.setName(name);
    customer.setEmail(email);
    var paypal = FACTORY.createPayPalAccount();
    paypal.setPayerEmail(payerEmail);
    customer.getPaymentMethods().add(paypal);
    return customer;
  }

  private tools.vitruv.methodologisttemplate.model.modelPayment.Order orderWithInvoice(
      String name, String invoiceNumber) {
    var order = FACTORY.createOrder();
    order.setName(name);
    var invoice = FACTORY.createInvoice();
    invoice.setInvoiceNumber(invoiceNumber);
    order.setInvoice(invoice);
    return order;
  }

  private tools.vitruv.dsls.vitruvocl.pipeline.BatchValidationResult evaluate(VirtualModel vsum) {
    VitruvOCL.registerVSUM(vsum);
    return VitruvOCL.evaluateConstraints(CONSTRAINT_FILE);
  }

  /** Asserts that the named constraint is violated. */
  private void assertViolated(VirtualModel vsum, String constraintName) {
    var result = evaluate(vsum);
    boolean violated =
        result.getResults().stream()
            .anyMatch(
                e -> !e.isSatisfied() && String.valueOf(e.getConstraint()).contains(constraintName));
    Assertions.assertTrue(
        violated, () -> "Expected violation of " + constraintName + " but got: " + result.getSummary());
  }

  private void addDomain(VirtualModel vsum, Path projectPath) {
    var view = view(vsum).withChangeDerivingTrait();
    view.registerRoot(
        FACTORY.createPaymentDomain(),
        URI.createFileURI(projectPath.toString() + "/example.modelpayment"));
    view.commitChanges();
  }

  private void modifyDomain(VirtualModel vsum, Consumer<PaymentDomain> modification) {
    var view = view(vsum).withChangeDerivingTrait();
    modification.accept(view.getRootObjects(PaymentDomain.class).iterator().next());
    view.commitChanges();
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
    return createView(
        vsum,
        List.of(PaymentDomain.class, tools.vitruv.methodologisttemplate.model.model2.Root.class));
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
