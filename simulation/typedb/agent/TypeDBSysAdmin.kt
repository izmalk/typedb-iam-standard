package com.vaticle.typedb.iam.simulation.typedb.agent

import com.vaticle.typedb.client.api.TypeDBOptions
import com.vaticle.typedb.client.api.TypeDBSession
import com.vaticle.typedb.client.api.TypeDBTransaction.Type.READ
import com.vaticle.typedb.client.api.TypeDBTransaction.Type.WRITE
import com.vaticle.typedb.iam.simulation.agent.SysAdmin
import com.vaticle.typedb.iam.simulation.common.Context
import com.vaticle.typedb.iam.simulation.common.Util.iterationDate
import com.vaticle.typedb.iam.simulation.typedb.Labels.ACCESS
import com.vaticle.typedb.iam.simulation.typedb.Labels.ACCESSED_OBJECT
import com.vaticle.typedb.iam.simulation.typedb.Labels.ACTION
import com.vaticle.typedb.iam.simulation.typedb.Labels.ACTION_NAME
import com.vaticle.typedb.iam.simulation.typedb.Labels.ATTRIBUTE
import com.vaticle.typedb.iam.simulation.typedb.Labels.CHANGE_REQUEST
import com.vaticle.typedb.iam.simulation.typedb.Labels.COMPANY
import com.vaticle.typedb.iam.simulation.typedb.Labels.COMPANY_MEMBER
import com.vaticle.typedb.iam.simulation.typedb.Labels.COMPANY_MEMBERSHIP
import com.vaticle.typedb.iam.simulation.typedb.Labels.EMAIL
import com.vaticle.typedb.iam.simulation.typedb.Labels.ENTITY
import com.vaticle.typedb.iam.simulation.typedb.Labels.FULL_NAME
import com.vaticle.typedb.iam.simulation.typedb.Labels.GROUP_MEMBER
import com.vaticle.typedb.iam.simulation.typedb.Labels.GROUP_MEMBERSHIP
import com.vaticle.typedb.iam.simulation.typedb.Labels.GROUP_OWNER
import com.vaticle.typedb.iam.simulation.typedb.Labels.GROUP_OWNERSHIP
import com.vaticle.typedb.iam.simulation.typedb.Labels.ID
import com.vaticle.typedb.iam.simulation.typedb.Labels.NAME
import com.vaticle.typedb.iam.simulation.typedb.Labels.OBJECT
import com.vaticle.typedb.iam.simulation.typedb.Labels.OWNED
import com.vaticle.typedb.iam.simulation.typedb.Labels.OWNED_GROUP
import com.vaticle.typedb.iam.simulation.typedb.Labels.OWNER
import com.vaticle.typedb.iam.simulation.typedb.Labels.OWNERSHIP
import com.vaticle.typedb.iam.simulation.typedb.Labels.PARENT_COMPANY
import com.vaticle.typedb.iam.simulation.typedb.Labels.PARENT_GROUP
import com.vaticle.typedb.iam.simulation.typedb.Labels.PERMISSION
import com.vaticle.typedb.iam.simulation.typedb.Labels.PERMITTED_ACCESS
import com.vaticle.typedb.iam.simulation.typedb.Labels.PERMITTED_SUBJECT
import com.vaticle.typedb.iam.simulation.typedb.Labels.PERSON
import com.vaticle.typedb.iam.simulation.typedb.Labels.REQUESTED_CHANGE
import com.vaticle.typedb.iam.simulation.typedb.Labels.REQUESTED_SUBJECT
import com.vaticle.typedb.iam.simulation.typedb.Labels.REQUESTING_SUBJECT
import com.vaticle.typedb.iam.simulation.typedb.Labels.REVIEW_DATE
import com.vaticle.typedb.iam.simulation.typedb.Labels.SUBJECT
import com.vaticle.typedb.iam.simulation.typedb.Labels.USER
import com.vaticle.typedb.iam.simulation.typedb.Labels.USER_GROUP
import com.vaticle.typedb.iam.simulation.typedb.Labels.VALIDITY
import com.vaticle.typedb.iam.simulation.typedb.Labels.VALID_ACTION
import com.vaticle.typedb.iam.simulation.typedb.Util.getRandomEntity
import com.vaticle.typedb.iam.simulation.typedb.concept.*
import com.vaticle.typedb.iam.simulation.common.`object`.Company
import com.vaticle.typedb.iam.simulation.common.`object`.Person
import com.vaticle.typedb.iam.simulation.typedb.Labels.OBJECT_OWNER
import com.vaticle.typedb.iam.simulation.typedb.Labels.OBJECT_OWNERSHIP
import com.vaticle.typedb.iam.simulation.typedb.Labels.OWNED_OBJECT
import com.vaticle.typedb.iam.simulation.typedb.Labels.PARENT_COMPANY_NAME
import com.vaticle.typedb.simulation.common.seed.RandomSource
import com.vaticle.typedb.simulation.typedb.TypeDBClient
import com.vaticle.typeql.lang.TypeQL.*
import java.lang.IllegalArgumentException
import kotlin.streams.toList

class TypeDBSysAdmin(client: TypeDBClient, context:Context): SysAdmin<TypeDBSession>(client, context) {
    private val options: TypeDBOptions = TypeDBOptions.core().infer(true)

    override fun addUser(session: TypeDBSession, company: Company, randomSource: RandomSource): List<Report> {
        val user = Person.initialise(company, context.seedData, randomSource)

        session.transaction(WRITE).use { transaction ->
            transaction.query().insert(
                match(
                    `var`(C).isa(COMPANY)
                        .has(NAME, company.name),
                ).insert(
                    `var`(S).isa(PERSON)
                        .has(FULL_NAME, user.name)
                        .has(EMAIL, user.email),
                    rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, S).isa(COMPANY_MEMBERSHIP)
                )
            )

            transaction.commit()
        }

        return listOf<Report>()
    }

    override fun removeUser(session: TypeDBSession, company: Company, randomSource: RandomSource): List<Report> {
        val userType = randomSource.choose(TypeDBSubjectType.values().asList().filter { it.type == USER })
        deleteSubject(session, company, randomSource, userType)
        return listOf<Report>()
    }

    override fun createUserGroup(session: TypeDBSession, company: Company, randomSource: RandomSource): List<Report> {
        val groupType = randomSource.choose(TypeDBSubjectType.values().asList().filter { it.type == USER_GROUP && it.generable })

        val group = when (groupType) {
            TypeDBSubjectType.PERSON -> throw IllegalArgumentException()
            TypeDBSubjectType.BUSINESS_UNIT -> throw IllegalArgumentException()
            TypeDBSubjectType.USER_ROLE -> throw IllegalArgumentException()
            TypeDBSubjectType.USER_ACCOUNT -> TypeDBUserAccount.initialise(company, context.seedData, randomSource).asSubject()
        }

        val owner = getRandomEntity(session, company, randomSource, SUBJECT)?.asSubject() ?: return listOf<Report>()

        session.transaction(WRITE, options).use { transaction ->
            transaction.query().insert(
                match(
                    `var`(C).isa(COMPANY)
                        .has(NAME, company.name),
                    `var`(S_OWNER).isa(owner.type)
                        .has(owner.idType, owner.idValue),
                    rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, S_OWNER).isa(COMPANY_MEMBERSHIP)
                ).insert(
                    `var`(S).isa(group.type)
                        .has(group.idType, group.idValue),
                    rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, S).isa(COMPANY_MEMBERSHIP),
                    rel(OWNED_GROUP, S).rel(GROUP_OWNER, S_OWNER).isa(GROUP_OWNERSHIP)
                )
            )

            transaction.commit()
        }

        return listOf<Report>()
    }

    override fun deleteUserGroup(session: TypeDBSession, company: Company, randomSource: RandomSource): List<Report> {
        val groupType = TypeDBSubjectType.USER_ACCOUNT
        deleteSubject(session, company, randomSource, groupType)
        return listOf<Report>()
    }

    override fun listSubjectGroupMemberships(session: TypeDBSession, company: Company, randomSource: RandomSource): List<Report> {
        val subject = getRandomEntity(session, company, randomSource, SUBJECT)?.asSubject() ?: return listOf<Report>()
        val groups: List<TypeDBSubject>

        session.transaction(READ, options).use { transaction ->
            groups = transaction.query().match(
                match(
                    `var`(S_MEMBER).isa(subject.type)
                        .has(PARENT_COMPANY_NAME, company.name)
                        .has(subject.idType, subject.idValue),
                    `var`(S).isaX(`var`(S_TYPE))
                        .has(PARENT_COMPANY_NAME, company.name)
                        .has(`var`(S_ID)),
                    `var`(S_ID).isaX(`var`(S_ID_TYPE)),
                    rel(PARENT_GROUP, S).rel(GROUP_MEMBER, S_MEMBER).isa(GROUP_MEMBERSHIP),
                    `var`(S_TYPE).sub(USER_GROUP),
                    `var`(S_ID_TYPE).sub(ID)
                )
            ).toList().map {
                TypeDBSubject(
                    it[S_TYPE],
                    it[S_ID_TYPE],
                    it[S_ID]
                )
            }
        }

        return listOf<Report>()
    }

    override fun listSubjectPermissions(session: TypeDBSession, company: Company, randomSource: RandomSource): List<Report> {
        val subject = getRandomEntity(session, company, randomSource, SUBJECT)?.asSubject() ?: return listOf<Report>()
        val permissions: List<TypeDBPermission>

        session.transaction(READ, options).use { transaction ->
            permissions = transaction.query().match(
                match(
                    `var`(S).isa(subject.type)
                        .has(PARENT_COMPANY_NAME, company.name)
                        .has(subject.idType, subject.idValue),
                    `var`(O).isaX(`var`(O_TYPE))
                        .has(PARENT_COMPANY_NAME, company.name)
                        .has(`var`(O_ID)),
                    `var`(O_ID).isaX(`var`(O_ID_TYPE)),
                    `var`(A).isaX(`var`(A_TYPE))
                        .has(PARENT_COMPANY_NAME, company.name)
                        .has(ACTION_NAME, `var`(A_NAME)),
                    `var`(AC).rel(ACCESSED_OBJECT, O).rel(VALID_ACTION, A).isa(ACCESS),
                    `var`(P).rel(PERMITTED_SUBJECT, S).rel(PERMITTED_ACCESS, AC).isa(PERMISSION)
                        .has(VALIDITY, `var`(P_VALIDITY))
                        .has(REVIEW_DATE, `var`(P_DATE)),
                    `var`(O_TYPE).sub(OBJECT),
                    `var`(O_ID_TYPE).sub(ID),
                    `var`(A_TYPE).sub(ACTION)
                )
            ).toList().map {
                TypeDBPermission(
                    subject,
                    TypeDBAccess(
                        TypeDBObject(it[O_TYPE], it[O_ID_TYPE], it[O_ID]),
                        TypeDBAction(it[A_TYPE], it[A_NAME])
                    ),
                    it[P_VALIDITY],
                    it[P_DATE]
                )
            }
        }

        return listOf<Report>()
    }

    override fun listObjectPermissionHolders(session: TypeDBSession, company: Company, randomSource: RandomSource): List<Report> {
        val `object` = getRandomEntity(session, company, randomSource, OBJECT)?.asObject() ?: return listOf<Report>()
        val permissions: List<TypeDBPermission>

        session.transaction(READ, options).use { transaction ->
            permissions = transaction.query().match(
                match(
                    `var`(O).isa(`object`.type)
                        .has(PARENT_COMPANY_NAME, company.name)
                        .has(`object`.idType, `object`.idValue),
                    `var`(S).isaX(`var`(S_TYPE))
                        .has(PARENT_COMPANY_NAME, company.name)
                        .has(`var`(S_ID)),
                    `var`(S_ID).isaX(`var`(S_ID_TYPE)),
                    `var`(A).isaX(`var`(A_TYPE))
                        .has(PARENT_COMPANY_NAME, company.name)
                        .has(ACTION_NAME, `var`(A_NAME)),
                    `var`(AC).rel(ACCESSED_OBJECT, O).rel(VALID_ACTION, A).isa(ACCESS),
                    `var`(P).rel(PERMITTED_SUBJECT, S).rel(PERMITTED_ACCESS, AC).isa(PERMISSION)
                        .has(VALIDITY, `var`(P_VALIDITY))
                        .has(REVIEW_DATE, `var`(P_DATE)),
                    `var`(S_TYPE).sub(SUBJECT),
                    `var`(S_ID_TYPE).sub(ID),
                    `var`(A_TYPE).sub(ACTION)
                )
            ).toList().map {
                TypeDBPermission(
                    TypeDBSubject(it[S_TYPE], it[S_ID_TYPE], it[S_ID]),
                    TypeDBAccess(
                        `object`,
                        TypeDBAction(it[A_TYPE], it[A_NAME])
                    ),
                    it[P_VALIDITY],
                    it[P_DATE]
                )
            }
        }

        return listOf<Report>()
    }

    override fun reviewChangeRequests(session: TypeDBSession, company: Company, randomSource: RandomSource): List<Report> {
        val requests: List<TypeDBChangeRequest>

        session.transaction(READ, options).use { transaction ->
            requests = transaction.query().match(
                match(
                    `var`(O).isaX(`var`(O_TYPE))
                        .has(PARENT_COMPANY_NAME, company.name)
                        .has(`var`(O_ID)),
                    `var`(O_ID).isaX(`var`(O_ID_TYPE)),
                    `var`(A).isa(`var`(A_TYPE))
                        .has(PARENT_COMPANY_NAME, company.name)
                        .has(ACTION_NAME, `var`(A_NAME)),
                    `var`(AC).rel(ACCESSED_OBJECT, O).rel(VALID_ACTION, A).isa(ACCESS),
                    `var`(S_REQUESTING).isaX(`var`(S_REQUESTING_TYPE))
                        .has(PARENT_COMPANY_NAME, company.name)
                        .has(`var`(S_REQUESTING_ID)),
                    `var`(S_REQUESTING_ID).isaX(`var`(S_REQUESTING_ID_TYPE)),
                    `var`(S_REQUESTED).isaX(`var`(S_REQUESTED_TYPE))
                        .has(PARENT_COMPANY_NAME, company.name)
                        .has(`var`(S_REQUESTED_ID)),
                    `var`(S_REQUESTED_ID).isaX(`var`(S_REQUESTED_ID_TYPE)),
                    rel(REQUESTING_SUBJECT, S_REQUESTING).rel(REQUESTED_SUBJECT, S_REQUESTED).rel(REQUESTED_CHANGE, AC).isa(CHANGE_REQUEST),
                    `var`(O_TYPE).sub(OBJECT),
                    `var`(O_ID_TYPE).sub(ID),
                    `var`(A_TYPE).sub(ACTION),
                    `var`(S_REQUESTING_TYPE).sub(SUBJECT),
                    `var`(S_REQUESTING_ID_TYPE).sub(ID),
                    `var`(S_REQUESTED_TYPE).sub(SUBJECT),
                    `var`(S_REQUESTED_ID_TYPE).sub(ID)
                )
            ).toList().map {
                TypeDBChangeRequest(
                    TypeDBSubject(it[S_REQUESTING_TYPE], it[S_REQUESTING_ID_TYPE], it[S_REQUESTING_ID]),
                    TypeDBSubject(it[S_REQUESTED_TYPE], it[S_REQUESTED_ID_TYPE], it[S_REQUESTED_ID]),
                    TypeDBAccess(
                        TypeDBObject(it[O_TYPE], it[O_ID_TYPE], it[O_ID]),
                        TypeDBAction(it[A_TYPE], it[A_NAME])
                    )
                )
            }
        }

        requests.forEach { request ->
            val requestingSubject = request.requestingSubject
            val requestedSubject = request.requestedSubject
            val accessedObject = request.requestedAccess.accessedObject
            val validAction = request.requestedAccess.validAction

            session.transaction(WRITE, options).use { transaction ->
                transaction.query().delete(
                    match(
                        `var`(O).isa(accessedObject.type)
                            .has(accessedObject.idType, accessedObject.idValue),
                        `var`(A).isa(validAction.type)
                            .has(validAction.idType, validAction.idValue),
                        `var`(AC).rel(ACCESSED_OBJECT, O).rel(VALID_ACTION, A).isa(ACCESS),
                        `var`(S_REQUESTING).isa(requestingSubject.type)
                            .has(requestingSubject.idType, requestingSubject.idValue),
                        `var`(S_REQUESTED).isa(requestedSubject.type)
                            .has(requestedSubject.idType, requestedSubject.idValue),
                        `var`(R).rel(REQUESTING_SUBJECT, S_REQUESTING).rel(REQUESTED_SUBJECT, S_REQUESTED).rel(REQUESTED_CHANGE, AC).isa(CHANGE_REQUEST),
                        `var`(C).isa(COMPANY)
                            .has(NAME, company.name),
                        rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, O).isa(COMPANY_MEMBERSHIP),
                        rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, A).isa(COMPANY_MEMBERSHIP),
                        rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, S_REQUESTING).isa(COMPANY_MEMBERSHIP),
                        rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, S_REQUESTED).isa(COMPANY_MEMBERSHIP)
                    ).delete(
                        `var`(R).isa(CHANGE_REQUEST)
                    )
                )

                if (randomSource.nextInt(100) < context.model.requestApprovalPercentage) {
                    transaction.query().insert(
                        match(
                            `var`(S).isa(requestedSubject.type)
                                .has(requestedSubject.idType, requestedSubject.idValue),
                            `var`(O).isa(accessedObject.type)
                                .has(accessedObject.idType, accessedObject.idValue),
                            `var`(A).isa(validAction.type)
                                .has(validAction.idType, validAction.idValue),
                            `var`(AC).rel(ACCESSED_OBJECT, O).rel(VALID_ACTION, A).isa(ACCESS),
                            `var`(C).isa(COMPANY)
                                .has(NAME, company.name),
                            rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, S).isa(COMPANY_MEMBERSHIP),
                            rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, O).isa(COMPANY_MEMBERSHIP),
                            rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, A).isa(COMPANY_MEMBERSHIP)
                        ).insert(
                            rel(PERMITTED_SUBJECT, S).rel(PERMITTED_ACCESS, AC).isa(PERMISSION)
                                .has(REVIEW_DATE, iterationDate(context.iterationNumber + context.model.permissionReviewAge))
                        )
                    )
                }

                transaction.commit()
            }
        }

        return listOf<Report>()
    }

    override fun collectGarbage(session: TypeDBSession, company: Company, randomSource: RandomSource): List<Report> {
        session.transaction(WRITE, options).use { transaction ->
            transaction.query().delete(
                match(
                    `var`(AT).isa(ATTRIBUTE),
                    not(
                        `var`().has(ATTRIBUTE, `var`(AT))
                    ),
                    not(
                        `var`(AT).isa(PARENT_COMPANY_NAME)
                    )
                ).delete(
                    `var`(AT).isa(ATTRIBUTE)
                )
            )
        }

        return listOf<Report>()
    }

    private fun deleteSubject(session: TypeDBSession, company: Company, randomSource: RandomSource, subjectType: TypeDBSubjectType) {
        val subject = getRandomEntity(session, company, randomSource, subjectType.label)?.asSubject() ?: return
        val newOwner = getRandomEntity(session, company, randomSource, subjectType.label)?.asSubject() ?: return
        val ownedGroups: List<TypeDBSubject>

        session.transaction(READ, options).use { transaction ->
            ownedGroups = transaction.query().match(
                match(
                    `var`(S).isaX(`var`(S_TYPE))
                        .has(PARENT_COMPANY_NAME, company.name)
                        .has(`var`(S_ID)),
                    `var`(S_ID).isaX(`var`(S_ID_TYPE)),
                    `var`(S_OWNER).isa(subject.type)
                        .has(PARENT_COMPANY_NAME, company.name)
                        .has(subject.idType, subject.idValue),
                    rel(OWNED_GROUP, S).rel(GROUP_OWNER, S_OWNER).isa(GROUP_OWNERSHIP),
                    `var`(S_TYPE).sub(USER_GROUP),
                    `var`(S_ID_TYPE).sub(ID)
                )
            ).toList().map { TypeDBSubject(it[S_TYPE], it[S_ID_TYPE], it[S_ID]) }
        }

        val ownedObjects: List<TypeDBObject>

        session.transaction(READ, options).use { transaction ->
            ownedObjects = transaction.query().match(
                match(
                    `var`(O).isaX(`var`(O_TYPE))
                        .has(PARENT_COMPANY_NAME, company.name)
                        .has(`var`(O_ID)),
                    `var`(O_ID).isaX(`var`(O_ID_TYPE)),
                    `var`(S).isa(subject.type)
                        .has(PARENT_COMPANY_NAME, company.name)
                        .has(subject.idType, subject.idValue),
                    rel(OWNED_OBJECT, O).rel(OBJECT_OWNER, S).isa(OBJECT_OWNERSHIP),
                    `var`(O_TYPE).sub(OBJECT),
                    `var`(O_ID_TYPE).sub(ID)
                )
            ).toList().map { TypeDBObject(it[O_TYPE], it[O_ID_TYPE], it[O_ID]) }
        }

        session.transaction(WRITE, options).use { transaction ->
            ownedGroups.parallelStream().forEach { group ->
                transaction.query().delete(
                    match(
                        `var`(S).isa(group.type)
                            .has(group.idType, group.idValue),
                        `var`(S_OWNER).isa(subject.type)
                            .has(subject.idType, subject.idValue),
                        `var`(C).isa(COMPANY)
                            .has(NAME, company.name),
                        rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, S).isa(COMPANY_MEMBERSHIP),
                        rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, S_OWNER).isa(COMPANY_MEMBERSHIP),
                        `var`(OW).rel(OWNED_GROUP, S).rel(GROUP_OWNER, S_OWNER).isa(GROUP_OWNERSHIP)
                    ).delete(
                        `var`(OW).isa(GROUP_OWNERSHIP)
                    )
                )

                transaction.query().insert(
                    match(
                        `var`(S).isa(group.type)
                            .has(group.idType, group.idValue),
                        `var`(S_OWNER).isa(newOwner.type)
                            .has(newOwner.idType, newOwner.idValue),
                        `var`(C).isa(COMPANY)
                            .has(NAME, company.name),
                        rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, S).isa(COMPANY_MEMBERSHIP),
                        rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, S_OWNER).isa(COMPANY_MEMBERSHIP)
                    ).insert(
                        rel(OWNED_GROUP, S).rel(GROUP_OWNER, S_OWNER).isa(GROUP_OWNERSHIP)
                    )
                )
            }

            ownedObjects.parallelStream().forEach { `object` ->
                transaction.query().delete(
                    match(
                        `var`(O).isa(`object`.type)
                            .has(`object`.idType, `object`.idValue),
                        `var`(S).isa(subject.type)
                            .has(subject.idType, subject.idValue),
                        `var`(C).isa(COMPANY)
                            .has(NAME, company.name),
                        rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, O).isa(COMPANY_MEMBERSHIP),
                        rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, S).isa(COMPANY_MEMBERSHIP),
                        `var`(OW).rel(OWNED_OBJECT, O).rel(OBJECT_OWNER, S).isa(OBJECT_OWNERSHIP)
                    ).delete(
                        `var`(OW).isa(OBJECT_OWNERSHIP)
                    )
                )

                transaction.query().insert(
                    match(
                        `var`(O).isa(`object`.type)
                            .has(`object`.idType, `object`.idValue),
                        `var`(S).isa(newOwner.type)
                            .has(newOwner.idType, newOwner.idValue),
                        `var`(C).isa(COMPANY)
                            .has(NAME, company.name),
                        rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, O).isa(COMPANY_MEMBERSHIP),
                        rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, S).isa(COMPANY_MEMBERSHIP)
                    ).insert(
                        rel(OWNED_OBJECT, O).rel(OBJECT_OWNER, S).isa(OBJECT_OWNERSHIP)
                    )
                )
            }

            transaction.query().delete(
                match(
                    `var`(S).isa(subject.type)
                        .has(subject.idType, subject.idValue),
                    `var`(C).isa(COMPANY)
                        .has(NAME, company.name),
                    rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, S).isa(COMPANY_MEMBERSHIP),
                    `var`(ME).rel(S).isa(GROUP_MEMBERSHIP)
                ).delete(
                    `var`(ME).isa(GROUP_MEMBERSHIP)
                )
            )

            transaction.query().delete(
                match(
                    `var`(S).isa(subject.type)
                        .has(subject.idType, subject.idValue),
                    `var`(C).isa(COMPANY)
                        .has(NAME, company.name),
                    rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, S).isa(COMPANY_MEMBERSHIP),
                    `var`(P).rel(PERMITTED_SUBJECT, S).isa(PERMISSION)
                ).delete(
                    `var`(P).isa(PERMISSION)
                )
            )

            transaction.query().delete(
                match(
                    `var`(S).isa(subject.type)
                        .has(subject.idType, subject.idValue),
                    `var`(C).isa(COMPANY)
                        .has(NAME, company.name),
                    rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, S).isa(COMPANY_MEMBERSHIP),
                    `var`(R).rel(S).isa(CHANGE_REQUEST)
                ).delete(
                    `var`(R).isa(CHANGE_REQUEST)
                )
            )

            transaction.query().delete(
                match(
                    `var`(S).isa(subject.type)
                        .has(subject.idType, subject.idValue),
                    `var`(C).isa(COMPANY)
                        .has(NAME, company.name),
                    `var`(ME).rel(PARENT_COMPANY, C).rel(COMPANY_MEMBER, S).isa(COMPANY_MEMBERSHIP)
                ).delete(
                    `var`(S).isa(subject.type),
                    `var`(ME).isa(COMPANY_MEMBERSHIP)
                )
            )

            transaction.commit()
        }
    }

    companion object {
        private const val A = "a"
        private const val AC = "ac"
        private const val AT = "at"
        private const val A_NAME = "a-name"
        private const val A_TYPE = "a-type"
        private const val C = "c"
        private const val ME = "me"
        private const val O = "o"
        private const val OW = "ow"
        private const val O_ID = "o-id"
        private const val O_ID_TYPE = "o-id-type"
        private const val O_TYPE = "o-type"
        private const val P = "p"
        private const val P_DATE = "p-date"
        private const val P_VALIDITY = "p-validity"
        private const val R = "r"
        private const val S = "s"
        private const val S_ID = "s-id"
        private const val S_ID_TYPE = "s-id-type"
        private const val S_MEMBER = "s-member"
        private const val S_OWNER = "s-owner"
        private const val S_REQUESTED = "s-requested"
        private const val S_REQUESTED_ID = "s-requested-id"
        private const val S_REQUESTED_ID_TYPE = "s-requested-id-type"
        private const val S_REQUESTED_TYPE = "s-requested-type"
        private const val S_REQUESTING = "s-requesting"
        private const val S_REQUESTING_ID = "s-requesting-id"
        private const val S_REQUESTING_ID_TYPE = "s-requesting-id-type"
        private const val S_REQUESTING_TYPE = "s-requesting-type"
        private const val S_TYPE = "s-type"
    }
}
