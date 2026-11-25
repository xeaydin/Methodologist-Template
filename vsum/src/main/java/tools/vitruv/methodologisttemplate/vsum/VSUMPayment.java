package tools.vitruv.methodologisttemplate.vsum;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import mir.reactions.modelPayment2Model2.ModelPayment2Model2ChangePropagationSpecification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import tools.vitruv.change.atomic.uuid.Uuid;
import tools.vitruv.change.composite.description.PropagatedChange;
import tools.vitruv.change.composite.description.VitruviusChange;
import tools.vitruv.change.composite.propagation.ChangePropagationListener;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.vitruvocl.pipeline.VitruvOCL;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.methodologisttemplate.model.modelPayment.ModelPaymentFactory;
import tools.vitruv.methodologisttemplate.model.modelPayment.PaymentDomain;

/**
 * Example of a VSUM for {@code modelPayment} with automatic evaluation of the OCL constraints in
 * {@code constraintsPayment.ocl} after every change propagation.
 *
 * <p>Run from the repository root. The persisted VSUM is written to {@code vsumexample-payment/}.
 */
public class VSUMPayment {

  private static final Path CONSTRAINT_FILE =
      Path.of(
          "consistency/src/main/constraints/tools/vitruv/methodologisttemplate/consistency/constraintsPayment.ocl");

  private static final Path STORAGE_FOLDER = Path.of("vsumexample-payment").toAbsolutePath();

  public static void main(String[] args) throws IOException {
    Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
    tools.vitruv.dsls.reactions.runtime.correspondence.CorrespondencePackage.eINSTANCE.eClass();

    VirtualModel vsum = createVirtualModel();
    VitruvOCL.registerVSUM(vsum);

    vsum.addChangePropagationListener(
        new ChangePropagationListener() {
          @Override
          public void startedChangePropagation(VitruviusChange<Uuid> changeToPropagate) {
            // nothing to do before propagation
          }

          @Override
          public void finishedChangePropagation(Iterable<PropagatedChange> propagatedChanges) {
            VitruvOCL.evaluateConstraints(CONSTRAINT_FILE)
                .getResults()
                .forEach(
                    entry -> {
                      if (!entry.isSatisfied()) {
                        java.lang.System.err.println(
                            "[OCL VIOLATION] "
                                + entry.getConstraint()
                                + ": "
                                + entry.getWarningsSummary());
                      }
                    });
          }
        });

    // Registering the domain violates CustomerHasPaymentMethod once a customer without a payment
    // method is added below, which is reported by the listener.
    modifyView(
        getDefaultView(vsum).withChangeDerivingTrait(),
        v ->
            v.registerRoot(
                ModelPaymentFactory.eINSTANCE.createPaymentDomain(),
                URI.createFileURI(STORAGE_FOLDER.resolve("example.modelpayment").toString())));

    modifyView(
        getDefaultView(vsum).withChangeDerivingTrait(),
        v -> {
          var domain = v.getRootObjects(PaymentDomain.class).iterator().next();
          var customer = ModelPaymentFactory.eINSTANCE.createCustomer();
          customer.setName("Emir");
          customer.setEmail("emir@example.com");
          domain.getCustomers().add(customer);
        });
  }

  private static VirtualModel createVirtualModel() throws IOException {
    return new VirtualModelBuilder()
        .withStorageFolder(STORAGE_FOLDER)
        .withUserInteractorForResultProvider(
            new TestUserInteraction.ResultProvider(new TestUserInteraction()))
        .withChangePropagationSpecifications(new ModelPayment2Model2ChangePropagationSpecification())
        .buildAndInitialize();
  }

  private static View getDefaultView(VirtualModel vsum) {
    var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("default"));
    selector.getSelectableElements().forEach(it -> selector.setSelected(it, true));
    return selector.createView();
  }

  private static void modifyView(CommittableView view, Consumer<CommittableView> modification) {
    modification.accept(view);
    view.commitChanges();
  }
}
