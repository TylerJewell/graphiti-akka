<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Organizations](index.html)

<!-- </nav> -->

# Organizations

An *Organization* in Akka is the root of the management hierarchy and serves as a container for all *Projects* where *Services* are deployed. It provides the context in which users operate, both in the *Akka Console* and *Akka CLI*.

To switch between organizations, you must specify the target organization’s context.

## <a href="about:blank#_key_concepts"></a> Key Concepts

- <a href="manage-users.html">**User Membership**</a>: A user can belong to multiple organizations, but membership does not automatically grant access to the organization’s projects.
- <a href="regions.html">**Regions**</a>: Each organization has access to specific regions. Projects created within the organization are located in one or more of these regions.
- <a href="../projects/manage-project-access.html">**Role-Based Access**</a>: Membership and project access within an organization are managed through role bindings.

## <a href="about:blank#_details"></a> Details

- **Organization Administrator**: The first user of an organization is an Organization Administrator, who can invite or add users with different roles.
- **Project Ownership**: Each project is owned by a single organization. Users must ensure that their projects are associated with the correct organization.
- **Region Assignment**: Projects created for an organization are assigned to one or more of the organization’s available regions.

## <a href="about:blank#_usage"></a> Usage

You can determine which organizations the current user is a member of using the following command:

```command
akka organizations list
```
Example output:

```none
NAME        ID                                     ROLES
acme        1a4a9d5d-1234-5678-910a-9c8fb3700da7   superuser
```

|  | You can refer to the organization in `akka` commands using either the "friendly name" or the "ID" with the `--organization` flag. See the page on [managing organization users](manage-users.html) to find more, including the use of organization roles. |

## <a href="about:blank#_topics"></a> Topics

- [Managing organization users](manage-users.html)
- [Regions](regions.html)

<!-- <footer> -->
<!-- <nav> -->
[Akka Automated Operations](../akka-platform.html) [Manage users](manage-users.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->