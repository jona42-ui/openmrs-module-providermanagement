/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.providermanagement.api.db.hibernate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Criteria;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.openmrs.Person;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.RelationshipType;
import org.openmrs.module.providermanagement.Provider;
import org.openmrs.module.providermanagement.ProviderRole;
import org.openmrs.module.providermanagement.api.db.ProviderManagementDAO;
import org.openmrs.module.providermanagement.suggestion.ProviderSuggestion;
import org.openmrs.module.providermanagement.suggestion.SupervisionSuggestion;
import org.openmrs.module.providermanagement.suggestion.SupervisionSuggestionType;

import java.util.List;

/**
 * It is a default implementation of  {@link ProviderManagementDAO}.
 */
public class HibernateProviderManagementDAO implements ProviderManagementDAO {
	protected final Log log = LogFactory.getLog(this.getClass());
	
	private SessionFactory sessionFactory;

    /**
     * @param sessionFactory the sessionFactory to set
     */
    public void setSessionFactory(SessionFactory sessionFactory) {
	    this.sessionFactory = sessionFactory;
    }
    
	/**
     * @return the sessionFactory
     */
    public SessionFactory getSessionFactory() {
	    return sessionFactory;
    }

    @Override
    public List<ProviderRole> getAllProviderRoles(boolean includeRetired) {
        Criteria criteria = sessionFactory.getCurrentSession().createCriteria(ProviderRole.class);
        if (!includeRetired) {
            criteria.add(Restrictions.eq("retired", false));
        }
        return (List<ProviderRole>) criteria.list();
    }

    @Override
    public ProviderRole getProviderRole(Integer id) {
        return (ProviderRole) sessionFactory.getCurrentSession().get(ProviderRole.class, id);
    }

    @Override
    public ProviderRole getProviderRoleByUuid(String uuid) {
        Criteria criteria = sessionFactory.getCurrentSession().createCriteria(ProviderRole.class);
        criteria.add(Restrictions.eq("uuid", uuid));
        return (ProviderRole) criteria.uniqueResult();
    }

    @Override
    public List<ProviderRole> getProviderRolesByRelationshipType(RelationshipType relationshipType) {
        Criteria criteria = sessionFactory.getCurrentSession().createCriteria(ProviderRole.class);
        criteria.add(Restrictions.eq("retired", false));
        criteria = criteria.createCriteria("relationshipTypes").add(Restrictions.eq("relationshipTypeId", relationshipType.getId()));
        return (List<ProviderRole>) criteria.list();
    }

    @Override
    public List<ProviderRole> getProviderRolesBySuperviseeProviderRole(ProviderRole providerRole) {
        Criteria criteria = sessionFactory.getCurrentSession().createCriteria(ProviderRole.class);
        criteria.add(Restrictions.eq("retired", false));
        criteria = criteria.createCriteria("superviseeProviderRoles").add(Restrictions.eq("providerRoleId", providerRole.getId()));
        return (List<ProviderRole>) criteria.list();
    }

    @Override
    public void saveProviderRole(ProviderRole role) {
        sessionFactory.getCurrentSession().saveOrUpdate(role);
    }

    @Override
    public void deleteProviderRole(ProviderRole role) {
        sessionFactory.getCurrentSession().delete(role);
    }

    @Override
    public List<Person> getProviders(String name, String identifier, PersonAddress personAddress, List<PersonAttribute> personAttribute, List<ProviderRole> providerRoles, Boolean includeRetired) {

        // first, create the provider criteria
         Criteria criteria = sessionFactory.getCurrentSession().createCriteria(Provider.class);

        // we want the result to be a list of person
        criteria.setProjection(Property.forName("person"));

        // restrict to ignore retired if flag is set
        if (!includeRetired) {
            criteria.add(Restrictions.eq("retired", false));
        }

        // restrict to providers with a specific identifier, if specified
        if (identifier != null && identifier.length() > 0) {
            criteria.add(Restrictions.ilike("identifier", identifier, MatchMode.START));
        }

        // restrict to provider with one of set of provider roles, if specified
        if (providerRoles != null && providerRoles.size() > 0) {
            criteria.add(Restrictions.in("providerRole", providerRoles));
        }

        // create person criteria on top of the provider criteria
        criteria = criteria.createCriteria("person");

        // we only want distinct people
        criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);

        // ignore voided people
        criteria.add(Restrictions.eq("personVoided", false));

        // create the join to the person table
        criteria.createAlias("names", "name");

        // order by name
        criteria.addOrder(Order.asc("name.givenName"));
        criteria.addOrder(Order.asc("name.middleName"));
        criteria.addOrder(Order.asc("name.familyName"));

        // handle restricting by name if any names have been specified
        if (name != null && name.length() > 0) {
            name = name.replace(", ", " ");
            String[] names = name.split("\\s+");

            for (String n : names) {
                if (n != null && n.length() > 0) {
                    criteria.add(Restrictions.or(Restrictions.ilike("name.givenName", n, MatchMode.START), Restrictions.or(Restrictions
                            .ilike("name.familyName", n, MatchMode.START), Restrictions.or(Restrictions.ilike("name.middleName", n,
                            MatchMode.START), Restrictions.ilike("name.familyName2", n, MatchMode.START)))));
                }
            }
        }

        // handle querying by address if an address has been specified
        if (personAddress != null) {
            addAddressCriteria(criteria, personAddress);
        }

        // we only want distinct people
        // TODO: duplicate--can I remove one of these?
        criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
        return (List<Person>) criteria.list();

    }

    @Override
    public List<Provider> getProvidersByPerson(Person person, boolean includeRetired) {
        Criteria criteria = sessionFactory.getCurrentSession().createCriteria(Provider.class);

        criteria.add(Restrictions.eq("person", person));
        if (!includeRetired) {
            criteria.add(Restrictions.eq("retired", false));
        }
        criteria.addOrder(Order.asc("providerId"));

        @SuppressWarnings("unchecked")
        List<Provider> list = criteria.list();
        return list;
    }

    @Override
    public List<Provider> getProvidersByProviderRoles(List<ProviderRole> roles, boolean includeRetired) {
        Criteria criteria = sessionFactory.getCurrentSession().createCriteria(Provider.class);
        criteria.add(Restrictions.in("providerRole", roles));
        if (!includeRetired) {
            criteria.add(Restrictions.eq("retired", false));
        }
        criteria.addOrder(Order.asc("providerId"));
        @SuppressWarnings("unchecked")
        List<Provider> list = criteria.list();
        return list;
    }

    @Override
    public ProviderSuggestion getProviderSuggestion(Integer id) {
        return (ProviderSuggestion) sessionFactory.getCurrentSession().get(ProviderSuggestion.class, id);
    }

    @Override
    public ProviderSuggestion getProviderSuggestionByUuid(String uuid) {
        Criteria criteria = sessionFactory.getCurrentSession().createCriteria(ProviderSuggestion.class);
        criteria.add(Restrictions.eq("uuid", uuid));
        return (ProviderSuggestion) criteria.uniqueResult();
    }

    @Override
    public List<ProviderSuggestion> getProviderSuggestionsByRelationshipType(RelationshipType relationshipType) {
        Criteria criteria = sessionFactory.getCurrentSession().createCriteria(ProviderSuggestion.class);
        criteria.add(Restrictions.eq("retired", false));
        criteria.add(Restrictions.eq("relationshipType", relationshipType));
        return (List<ProviderSuggestion>) criteria.list();
    }

    @Override
    public void saveProviderSuggestion(ProviderSuggestion suggestion) {
        sessionFactory.getCurrentSession().saveOrUpdate(suggestion);
    }

    @Override
    public void deleteProviderSuggestion(ProviderSuggestion suggestion) {
        sessionFactory.getCurrentSession().delete(suggestion);
    }

    @Override
    public SupervisionSuggestion getSupervisionSuggestion(Integer id) {
        return (SupervisionSuggestion) sessionFactory.getCurrentSession().get(SupervisionSuggestion.class, id);
    }

    @Override
    public SupervisionSuggestion getSupervisionSuggestionByUuid(String uuid) {
        Criteria criteria = sessionFactory.getCurrentSession().createCriteria(SupervisionSuggestion.class);
        criteria.add(Restrictions.eq("uuid", uuid));
        return (SupervisionSuggestion) criteria.uniqueResult();
    }

    @Override
    public List<SupervisionSuggestion> getSupervisionSuggestionsByProviderRoleAndSuggestionType(ProviderRole providerRole, SupervisionSuggestionType suggestionType) {
        Criteria criteria = sessionFactory.getCurrentSession().createCriteria(SupervisionSuggestion.class);
        criteria.add(Restrictions.eq("retired", false));
        criteria.add(Restrictions.eq("providerRole", providerRole));

        if (suggestionType != null) {
            criteria.add(Restrictions.eq("suggestionType", suggestionType));
        }

        return (List<SupervisionSuggestion>) criteria.list();
    }

    @Override
    public void saveSupervisionSuggestion(SupervisionSuggestion suggestion) {
        sessionFactory.getCurrentSession().saveOrUpdate(suggestion);
    }

    @Override
    public void deleteSupervisionSuggestion(SupervisionSuggestion suggestion) {
        sessionFactory.getCurrentSession().delete(suggestion);
    }

    private void addAddressCriteria(Criteria criteria, PersonAddress personAddress) {
        criteria.createAlias("addresses", "address");

        // check all the address fields, and add restrictions if necessary
        if (personAddress.getAddress1() != null) {
            criteria.add(Restrictions.ilike("address.address1", personAddress.getAddress1(), MatchMode.ANYWHERE));
        }
        if (personAddress.getAddress2() != null) {
            criteria.add(Restrictions.ilike("address.address2", personAddress.getAddress2(), MatchMode.ANYWHERE));
        }
        if (personAddress.getAddress3() != null) {
            criteria.add(Restrictions.ilike("address.address3", personAddress.getAddress3(), MatchMode.ANYWHERE));
        }
        if (personAddress.getAddress4() != null) {
            criteria.add(Restrictions.ilike("address.address4", personAddress.getAddress4(), MatchMode.ANYWHERE));
        }
        if (personAddress.getAddress5() != null) {
            criteria.add(Restrictions.ilike("address.address5", personAddress.getAddress5(), MatchMode.ANYWHERE));
        }
        if (personAddress.getAddress6() != null) {
            criteria.add(Restrictions.ilike("address.address6", personAddress.getAddress6(), MatchMode.ANYWHERE));
        }
        if (personAddress.getCityVillage() != null) {
            criteria.add(Restrictions.ilike("address.cityVillage", personAddress.getCityVillage(), MatchMode.ANYWHERE));
        }
        if (personAddress.getCountry() != null) {
            criteria.add(Restrictions.ilike("address.country", personAddress.getCountry(), MatchMode.ANYWHERE));
        }
        if (personAddress.getCountyDistrict() != null) {
            criteria.add(Restrictions.ilike("address.countyDistrict", personAddress.getCountyDistrict(), MatchMode.ANYWHERE));
        }
        if (personAddress.getStateProvince() != null) {
            criteria.add(Restrictions.ilike("address.stateProvince", personAddress.getStateProvince(), MatchMode.ANYWHERE));
        }
        if (personAddress.getPostalCode() != null) {
            criteria.add(Restrictions.ilike("address.postalCode", personAddress.getPostalCode(), MatchMode.ANYWHERE));
        }
    }
}