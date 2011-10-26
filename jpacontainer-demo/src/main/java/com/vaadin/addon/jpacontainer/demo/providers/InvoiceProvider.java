/*
JPAContainer
Copyright (C) 2009-2011 Oy Vaadin Ltd

This program is available under GNU Affero General Public License (version
3 or later at your option).

See the file licensing.txt distributed with this software for more
information about licensing.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.vaadin.addon.jpacontainer.demo.providers;

import com.vaadin.addon.jpacontainer.demo.domain.Invoice;
import org.springframework.stereotype.Repository;

/**
 * Entity provider for {@link Invoice}s that uses Spring's declarative
 * transaction annotations. It is also annotated with the {@link Repository} annotation,
 * which means that the Spring container will automatically detect it and
 * add it to the container.
 *
 * @author Petter Holmström (Vaadin Ltd)
 * @since 1.0
 */
@Repository(value = "invoiceProvider")
public class InvoiceProvider extends LocalEntityProviderBean<Invoice> {

    /**
     * Creates a new <code>InvoiceProvider</code>.
     */
    public InvoiceProvider() {
        super(Invoice.class);
    }
}
