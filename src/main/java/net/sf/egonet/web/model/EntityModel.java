package net.sf.egonet.web.model;

import net.sf.egonet.model.Entity;
import net.sf.egonet.web.Main;

import org.apache.wicket.model.LoadableDetachableModel;
import org.hibernate.Session;
import org.hibernate.Transaction;

public class EntityModel extends LoadableDetachableModel
{
	private Long id;
	private String className;
	private Entity nonPersistedEntity;

	private transient Session session;
	private transient Transaction tx;

	public EntityModel(Entity entity)
   	{
		super(); // Entity may be detached with uninitialized (lazy) fields, so EntityModel must start detached.
		this.id = entity.getId();
		this.className = entity.getClass().getCanonicalName();
		if(id == null) {
			nonPersistedEntity = entity; // Doesn't have id yet, so hasn't been persisted.
		}
	}

	public void save() {
		Entity entity = (Entity) getObject();
		if(entity != null) {
			ensureSessionAvailable();
			if(entity.getId() == null) {
				this.id = (Long) session.save(entity);
			} else {
				session.saveOrUpdate(entity);
			}
		}
		nonPersistedEntity = null; // Just saved, so entity is definitely persisted.
	}

	private void ensureSessionAvailable() {
		if(session == null || tx == null) {
			session = Main.getDBSessionFactory().openSession();
			tx = session.beginTransaction();
		}
	}

	@Override
	protected Object load() {
		if(this.id == null) {
			return nonPersistedEntity;
		}
		ensureSessionAvailable();
		return session.createQuery("from "+className+" e where e.id = :id")
			.setLong("id", id)
			.uniqueResult();
	}

	protected void onDetach() {
		if(tx != null) {
			tx.commit();
		}
		if(session != null) {
			session.close();
		}
		tx = null;
		session = null;
		if(nonPersistedEntity != null && nonPersistedEntity.getId() != null) {
			this.id = nonPersistedEntity.getId();
			nonPersistedEntity = null;
		}
	}
}
