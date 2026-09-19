package ecommerce_event_driven.shipment.modules.shipment.domain.models;

import java.util.HashSet;
import java.util.Set;

/**
 * Valida transicoes de status de envio conforme o contrato.
 */
public class ShipmentStateMachine {
    private static final Set<Transition> VALID_TRANSITIONS = new HashSet<>();

    static {
        // null -> pending (criacao)
        VALID_TRANSITIONS.add(new Transition(null, "pending"));
        // pending -> ready_to_ship (loja)
        VALID_TRANSITIONS.add(new Transition("pending", "ready_to_ship"));
        // ready_to_ship -> in_transit (loja)
        VALID_TRANSITIONS.add(new Transition("ready_to_ship", "in_transit"));
        // in_transit -> out_for_delivery (loja, opcional)
        VALID_TRANSITIONS.add(new Transition("in_transit", "out_for_delivery"));
        // in_transit -> delivered (comprador)
        VALID_TRANSITIONS.add(new Transition("in_transit", "delivered"));
        // out_for_delivery -> delivered (comprador)
        VALID_TRANSITIONS.add(new Transition("out_for_delivery", "delivered"));
        // in_transit -> returned (loja)
        VALID_TRANSITIONS.add(new Transition("in_transit", "returned"));
        // out_for_delivery -> returned (loja)
        VALID_TRANSITIONS.add(new Transition("out_for_delivery", "returned"));
        // pending -> cancelled (loja)
        VALID_TRANSITIONS.add(new Transition("pending", "cancelled"));
        // ready_to_ship -> cancelled (loja)
        VALID_TRANSITIONS.add(new Transition("ready_to_ship", "cancelled"));
    }

    public static void validateTransition(String from, String to) {
        if (!isValidTransition(from, to)) {
            throw new IllegalArgumentException(
                    String.format("Transicao invalida: %s -> %s", from, to));
        }
    }

    public static boolean isValidTransition(String from, String to) {
        return VALID_TRANSITIONS.contains(new Transition(from, to));
    }

    private static class Transition {
        private final String from;
        private final String to;

        Transition(String from, String to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Transition other)) return false;
            return (from == null ? other.from == null : from.equals(other.from)) &&
                    (to == null ? other.to == null : to.equals(other.to));
        }

        @Override
        public int hashCode() {
            return (from != null ? from.hashCode() : 0) +
                    (to != null ? to.hashCode() : 0) * 31;
        }
    }
}
