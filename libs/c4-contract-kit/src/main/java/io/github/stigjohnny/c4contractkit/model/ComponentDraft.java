package io.github.stigjohnny.c4contractkit.model;

import com.structurizr.model.Component;
import java.util.List;
import java.util.Optional;

/** A component as read from the model; id and status are empty where the model gives no valid value. */
record ComponentDraft(Component element, String name, Optional<String> id, Optional<ComponentStatus> status,
                      List<String> packages) {

    /** Called only when the model has no problem, so id and status are present. */
    ContractComponent toContract() {
        return new ContractComponent(id.orElseThrow(), element.getName(), status.orElseThrow(), packages);
    }
}
