-- Reverts V3 in development and CI only; deletes every shipment. Run as shipping_owner in the transaction that
-- removes version 3 from Flyway history, only while V3 is latest (ADR 0016). No cascade.
drop table shipments.shipment;
drop sequence shipments.tracking_number_seq;
drop schema shipments;
